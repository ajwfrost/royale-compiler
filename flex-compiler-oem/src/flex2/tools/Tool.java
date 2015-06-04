/*
 *
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

package flex2.tools;

import flash.localization.LocalizationManager;
import flex2.compiler.Logger;
import flex2.compiler.config.ConfigurationException;
import flex2.compiler.util.CompilerMessage;
import flex2.compiler.util.CompilerMessage.CompilerError;
import flex2.compiler.util.ThreadLocalToolkit;
import flex2.tools.oem.Message;
import org.apache.flex.compiler.clients.COMPC;
import org.apache.flex.compiler.clients.MXMLC;
import org.apache.flex.compiler.clients.MXMLJSC;
import org.apache.flex.compiler.clients.MXMLJSC.JSOutputType;
import org.apache.flex.compiler.clients.problems.ProblemQuery;
import org.apache.flex.compiler.clients.problems.ProblemQueryProvider;
import org.apache.flex.compiler.config.Configuration;
import org.apache.flex.compiler.config.ConfigurationBuffer;
import org.apache.flex.compiler.config.ConfigurationValue;
import org.apache.flex.compiler.filespecs.FileSpecification;
import org.apache.flex.compiler.internal.config.FileConfigurator;
import org.apache.flex.compiler.problems.CompilerProblemSeverity;
import org.apache.flex.compiler.problems.ICompilerProblem;
import org.apache.flex.compiler.problems.annotations.DefaultSeverity;
import org.apache.flex.utils.ArgumentUtil;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.InvocationTargetException;
import java.util.*;

import static org.apache.flex.compiler.clients.MXMLJSC.JSOutputType.FLEXJS_DUAL;

/**
 * Common base class for most flex tools.
 */
public class Tool {
    protected static Class<? extends MXMLC> COMPILER;
    protected static Class<? extends MxmlJSC> JS_COMPILER;

    protected static int compile(String[] args) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        int exitCode;

        final String value = ArgumentUtil.getValue(args, "-js-output-type");
        JSOutputType jsOutputType = null;

        if (value != null)
        {
            jsOutputType = JSOutputType.fromString(value);
        }

        if (jsOutputType != null) {
            ArgumentBag bag = preparePhase1(new ArgumentBag(args));

            MxmlJSC mxmlJSC = JS_COMPILER.newInstance();
            exitCode = mxmlJSC.execute(bag.args);

            if (!mxmlJSC.getProblemQuery().hasErrors()) {
                if (jsOutputType.equals(FLEXJS_DUAL)) {
                    preparePhase2(bag);
                    exitCode = flexCompile(bag.args);
                }
            } else {
                processProblemReport(mxmlJSC);
            }
        } else {
            exitCode = flexCompile(args);
        }

        return exitCode;
    }

    protected static ArgumentBag preparePhase1(ArgumentBag bag) {
        bag.isCommandLineOutput = true;

        bag.oldOutputPath = ArgumentUtil.getValue(bag.args, "-output");

        if (bag.oldOutputPath == null) {
            bag.oldOutputPath = ArgumentUtil.getValue(bag.args, "-o");
            if (bag.oldOutputPath != null) {
                bag.outputAlias = "-o";
            }
        } else {
            bag.outputAlias = "-output";
        }

        if (bag.oldOutputPath == null) {
            bag.isCommandLineOutput = false;
            ConfigurationBuffer flexConfig = null;

            try {
                flexConfig = loadConfig(bag.args);
            } catch (org.apache.flex.compiler.exceptions.ConfigurationException ignored) {
            }

            final List<ConfigurationValue> output = flexConfig != null ? flexConfig.getVar("output") : null;
            final ConfigurationValue configurationValue = output != null ? output.get(0) : null;
            bag.oldOutputPath = configurationValue != null ? configurationValue.getArgs().get(0) : null;
        }

        if (bag.oldOutputPath != null) {
            final int lastIndexOf = Math.max(bag.oldOutputPath.lastIndexOf("/"), bag.oldOutputPath.lastIndexOf("\\"));

            if (lastIndexOf > -1) {
                bag.newOutputPath = bag.oldOutputPath.substring(0, lastIndexOf) + File.separator + "js" + File.separator + "out";

                if (bag.isCommandLineOutput) {
                    ArgumentUtil.setValue(bag.args, bag.outputAlias, bag.newOutputPath);
                } else {
                    bag.args = ArgumentUtil.addValueAt(bag.args, "-output", bag.newOutputPath, bag.args.length - 1);
                }
            }
        }

        return bag;
    }

    protected static ArgumentBag preparePhase2(ArgumentBag bag) {
        bag.args = ArgumentUtil.removeElement(bag.args, "-js-output-type");

        if (bag.oldOutputPath != null) {
            if (bag.isCommandLineOutput) {
                ArgumentUtil.setValue(bag.args, bag.outputAlias, bag.oldOutputPath);
            } else {
                bag.args = ArgumentUtil.removeElement(bag.args, "-output");
            }
        }

        if (COMPILER.getName().equals(COMPC.class.getName())) {
            bag.args = ArgumentUtil.addValueAt(bag.args, "-include-file", "js" + File.separator + "out" + File.separator + "*," + bag.newOutputPath, bag.args.length - 1);
        }

        return bag;
    }

    private static ConfigurationBuffer loadConfig(String[] args) throws org.apache.flex.compiler.exceptions.ConfigurationException {
        final String configFilePath = ArgumentUtil.getValue(args, "-load-config");
        final File configFile = new File(configFilePath);
        final FileSpecification fileSpecification = new FileSpecification(configFile.getAbsolutePath());
        final ConfigurationBuffer cfgbuf = createConfigurationBuffer(Configuration.class);

        FileConfigurator.load(
                cfgbuf,
                fileSpecification,
                new File(configFile.getPath()).getParent(),
                "flex-config",
                false);

        return cfgbuf;
    }

    private static ConfigurationBuffer createConfigurationBuffer(
            Class<? extends Configuration> configurationClass) {
        return new ConfigurationBuffer(
                configurationClass, Configuration.getAliases());
    }

    protected static int flexCompile(String[] args) throws NoSuchMethodException, InstantiationException, IllegalAccessException, InvocationTargetException {
        int exitCode;

        MXMLC mxmlc = COMPILER.newInstance();
        exitCode = mxmlc.execute(args);
        processProblemReport(new CompilerRequestableProblems(mxmlc));

        return exitCode;
    }

    public static Map<String, String> getLicenseMapFromFile(String fileName) throws ConfigurationException {
        Map<String, String> result = null;
        FileInputStream in = null;

        try {
            in = new FileInputStream(fileName);
            Properties properties = new Properties();
            properties.load(in);
            Enumeration enumeration = properties.propertyNames();

            if (enumeration.hasMoreElements()) {
                result = new HashMap<String, String>();

                while (enumeration.hasMoreElements()) {
                    String propertyName = (String) enumeration.nextElement();
                    result.put(propertyName, properties.getProperty(propertyName));
                }
            }
        } catch (IOException ioException) {
            LocalizationManager l10n = ThreadLocalToolkit.getLocalizationManager();
            throw new ConfigurationException(l10n.getLocalizedTextString(new FailedToLoadLicenseFile(fileName)));
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ioe) {
                }
            }
        }

        return result;
    }

    public static class FailedToLoadLicenseFile extends CompilerError {
        private static final long serialVersionUID = -2980033917773108328L;

        public String fileName;

        public FailedToLoadLicenseFile(String fileName) {
            super();
            this.fileName = fileName;
        }
    }

    public static void processProblemReport(ProblemQueryProvider requestableProblems) {
        for (ICompilerProblem problem : requestableProblems.getProblemQuery().getProblems()) {
            Class aClass = problem.getClass();
            Annotation[] annotations = aClass.getAnnotations();

            for (Annotation annotation : annotations) {
                if (annotation instanceof DefaultSeverity) {
                    DefaultSeverity myAnnotation = (DefaultSeverity) annotation;
                    CompilerProblemSeverity cps = myAnnotation.value();
                    String level;
                    if (cps.equals(CompilerProblemSeverity.ERROR))
                        level = Message.ERROR;
                    else if (cps.equals(CompilerProblemSeverity.WARNING))
                        level = Message.WARNING;
                    else
                        break; // skip if IGNORE?
                    CompilerMessage msg = new CompilerMessage(level,
                            problem.getSourcePath(),
                            problem.getLine() + 1,
                            problem.getColumn());
                    try {
                        String errText = (String) aClass.getField("DESCRIPTION").get(aClass);
                        while (errText.contains("${")) {
                            int start = errText.indexOf("${");
                            int end = errText.indexOf("}", start);
                            String token = errText.substring(start + 2, end);
                            String value = (String) aClass.getField(token).get(problem);
                            token = "${" + token + "}";
                            errText = errText.replace(token, value);
                        }

                        msg.setMessage(errText);
                        logMessage(msg);
                    } catch (IllegalArgumentException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (SecurityException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (IllegalAccessException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    } catch (NoSuchFieldException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

        }
    }

    private static void logMessage(CompilerMessage msg) {

        final Logger logger = ThreadLocalToolkit.getLogger();

        if (logger != null) {
            if (msg.getLevel().equals(Message.INFO)) {
                logger.logInfo(msg.getPath(), msg.getLine(), msg.getColumn(), msg.getMessage());
            } else if (msg.getLevel().equals(Message.WARNING)) {
                logger.logWarning(msg.getPath(), msg.getLine(), msg.getColumn(), msg.getMessage());
            } else if (msg.getLevel().equals(Message.ERROR)) {
                logger.logError(msg.getPath(), msg.getLine(), msg.getColumn(), msg.getMessage());
            }
        }
    }

    public static class NoUpdateMessage extends CompilerMessage.CompilerInfo {
        private static final long serialVersionUID = 6943388392279226490L;
        public String name;

        public NoUpdateMessage(String name) {
            this.name = name;
        }
    }

    static class CompilerRequestableProblems implements ProblemQueryProvider {
        private MXMLC mxmlc;

        public CompilerRequestableProblems(MXMLC mxmlc) {
            this.mxmlc = mxmlc;
        }

        @Override
        public ProblemQuery getProblemQuery() {
            return mxmlc.getProblems();
        }
    }

    protected static class ArgumentBag {
        public String[] args;

        public String outputAlias;
        public String oldOutputPath;
        public String newOutputPath;
        public boolean isCommandLineOutput;

        public ArgumentBag(String[] args) {
            this.args = args;
        }
    }
}
