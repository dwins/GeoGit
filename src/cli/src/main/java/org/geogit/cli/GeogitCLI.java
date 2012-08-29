/* Copyright (c) 2011 TOPP - www.openplans.org. All rights reserved.
 * This code is licensed under the LGPL 2.1 license, available at the root
 * application directory.
 */
package org.geogit.cli;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.File;
import java.io.IOException;
import java.text.NumberFormat;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Level;

import javax.annotation.Nullable;

import jline.console.ConsoleReader;
import jline.console.CursorBuffer;

import org.geogit.api.GeoGIT;
import org.geogit.repository.Repository;
import org.geogit.storage.RepositoryDatabase;
import org.geogit.storage.bdbje.EntityStoreConfig;
import org.geogit.storage.bdbje.EnvironmentBuilder;
import org.geogit.storage.bdbje.JERepositoryDatabase;
import org.geotools.util.DefaultProgressListener;
import org.geotools.util.logging.Logging;
import org.opengis.util.ProgressListener;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.stereotype.Service;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.google.common.base.Throwables;
import com.sleepycat.je.Environment;

/**
 * Command Line Interface for geogit.
 * <p>
 * Looks up and executes {@link CLICommand} implementations on the classpath annotated with the
 * Spring's {@link Service @Service} annotation. If a command holds any state, make sure to also
 * annotate it with {@code @Scope(value = "prototype")} to account for any possible non-single run,
 * like when using the {@link GeogitConsole console application}.
 */
public class GeogitCLI {

    private ApplicationContext ctx;

    private Platform platform;

    private GeoGIT geogit;

    private ConsoleReader consoleReader;

    private DefaultProgressListener progressListener;

    /**
     * @param console
     */
    public GeogitCLI(final ConsoleReader consoleReader) {
        this.consoleReader = consoleReader;
        ctx = new AnnotationConfigApplicationContext("org.geogit.cli");
        platform = new DefaultPlatform();
    }

    public Platform getPlatform() {
        return platform;
    }

    public void setPlatform(Platform platform) {
        checkNotNull(platform);
        this.platform = platform;
    }

    /**
     * Provides a GeoGIT facade configured for the current repository if inside a repository,
     * {@code null} otherwise.
     * <p>
     * Note the repository is lazily loaded and cached afterwards to simplify the execution of
     * commands or command options that do not need a live repository.
     * 
     * @return
     */
    public synchronized GeoGIT getGeogit() {
        if (geogit == null) {
            GeoGIT geogit = loadRepository();
            setGeogit(geogit);
        }
        return geogit;
    }

    public void setGeogit(@Nullable GeoGIT geogit) {
        this.geogit = geogit;
    }

    /**
     * Loads the repository _if_ inside a geogit repository and returns a configured {@link GeoGIT}
     * facade.
     * 
     * @return a geogit for the current repository or {@code null} if not inside a geogit repository
     *         directory.
     */
    private GeoGIT loadRepository() {
        GeoGIT geogit = null;

        Platform platform = getPlatform();
        File envHome = new File(platform.pwd(), ".geogit");
        envHome.mkdirs();
        if (!envHome.exists()) {
            throw new RuntimeException("Unable to create geogit environment at '"
                    + envHome.getAbsolutePath() + "'");
        }

        File repositoryHome = new File(envHome, "objects");
        File indexHome = new File(envHome, "index");

        if (repositoryHome.exists()) {
            // Stopwatch sw = new Stopwatch().start();
            indexHome.mkdirs();

            EntityStoreConfig config = new EntityStoreConfig();
            config.setCacheMemoryPercentAllowed(50);
            EnvironmentBuilder esb = new EnvironmentBuilder(config);
            Properties bdbEnvProperties = null;
            Environment environment;
            environment = esb.buildEnvironment(repositoryHome, bdbEnvProperties);

            Environment stagingEnvironment;
            stagingEnvironment = esb.buildEnvironment(indexHome, bdbEnvProperties);

            RepositoryDatabase repositoryDatabase = new JERepositoryDatabase(environment,
                    stagingEnvironment);

            repositoryDatabase.create();

            Repository repository = new Repository(repositoryDatabase, envHome);

            geogit = new GeoGIT(repository);
        }

        return geogit;
    }

    public ConsoleReader getConsole() {
        return consoleReader;
    }

    public void close() {
        if (geogit != null) {
            geogit.getRepository().close();
            geogit = null;
        }
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        Logging.ALL.forceMonolineConsoleOutput();
        Logging.getLogger("org.springframework").setLevel(Level.WARNING);

        // TODO: revisit in case we need to grafefully shutdown upon CTRL+C
        // Runtime.getRuntime().addShutdownHook(new Thread() {
        // @Override
        // public void run() {
        // System.err.println("Shutting down...");
        // System.err.flush();
        // }
        // });

        ConsoleReader consoleReader;
        try {
            consoleReader = new ConsoleReader(System.in, System.out);
            // needed for CTRL+C not to let the console broken
            consoleReader.getTerminal().setEchoEnabled(true);
        } catch (Exception e) {
            throw Throwables.propagate(e);
        }

        int exitCode = 0;
        GeogitCLI cli = null;
        try {
            cli = new GeogitCLI(consoleReader);
            cli.execute(args);
        } catch (Exception e) {
            exitCode = -1;
            try {
                if (e instanceof ParameterException) {
                    consoleReader.println(e.getMessage() + ". See geogit --help.");
                    consoleReader.flush();
                } else if (e instanceof IllegalArgumentException
                        || e instanceof IllegalStateException) {
                    consoleReader.println(e.getMessage());
                    consoleReader.flush();
                } else {
                    e.printStackTrace();
                }
            } catch (IOException ioe) {
                ioe.printStackTrace();
            }
        } finally {
            try {
                if (cli != null) {
                    cli.close();
                }
            } finally {
                try {
                    consoleReader.getTerminal().restore();
                } catch (Exception e) {
                    e.printStackTrace();
                    exitCode = -1;
                }
            }
        }
        System.exit(exitCode);
    }

    public ApplicationContext getContext() {
        return ctx;
    }

    public Collection<CLICommand> findCommands() {
        Map<String, CLICommand> commandBeans = ctx.getBeansOfType(CLICommand.class);
        return commandBeans.values();
    }

    public JCommander newCommandParser() {
        JCommander jc = new JCommander(this);
        jc.setProgramName("geogit");
        for (CLICommand cmd : findCommands()) {
            jc.addCommand(cmd);
        }
        return jc;
    }

    /**
     * @param args
     */
    public void execute(String... args) throws Exception {
        JCommander jc = newCommandParser();
        jc.parse(args);
        final String parsedCommand = jc.getParsedCommand();
        if (null == parsedCommand) {
            jc.usage();
        } else {
            JCommander jCommander = jc.getCommands().get(parsedCommand);
            List<Object> objects = jCommander.getObjects();
            CLICommand cliCommand = (CLICommand) objects.get(0);
            cliCommand.run(this);
            getConsole().flush();
        }
    }

    public synchronized ProgressListener getProgressListener() {
        if (this.progressListener == null) {

            this.progressListener = new DefaultProgressListener() {

                private final Platform platform = getPlatform();

                private final ConsoleReader console = getConsole();

                private final NumberFormat fmt = NumberFormat.getPercentInstance();

                private final long delayMillis = 300;

                private volatile long lastRun = platform.currentTimeMillis();

                @Override
                public void complete() {
                    // avoid double logging if caller missbehaves
                    if (super.isCompleted()) {
                        return;
                    }
                    super.complete();
                    super.dispose();
                    try {
                        log(100f);
                        console.println();
                        console.flush();
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    }
                }

                @Override
                public void progress(float percent) {
                    super.progress(percent);
                    long currentTimeMillis = platform.currentTimeMillis();
                    if ((currentTimeMillis - lastRun) > delayMillis) {
                        lastRun = currentTimeMillis;
                        log(percent);
                    }
                }

                private void log(float percent) {
                    CursorBuffer cursorBuffer = console.getCursorBuffer();
                    cursorBuffer.clear();
                    cursorBuffer.write(fmt.format(percent / 100f));
                    try {
                        console.redrawLine();
                        console.flush();
                    } catch (IOException e) {
                        Throwables.propagate(e);
                    }
                }
            };

        }
        return this.progressListener;
    }
}
