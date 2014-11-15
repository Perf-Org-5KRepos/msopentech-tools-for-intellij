/**
 * Copyright 2014 Microsoft Open Technologies Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.microsoftopentechnologies.intellij.helpers.aadauth;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.microsoftopentechnologies.intellij.helpers.UIHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class BrowserLauncher {
    private String url;
    private String redirectUrl;
    private String callbackUrl;
    private String windowTitle;
    private Project project;

    private static URLClassLoader loader = null;

    public BrowserLauncher(
            String url,
            String redirectUrl,
            String callbackUrl,
            String windowTitle,
            Project project) {
        this.url = url;
        this.redirectUrl = redirectUrl;
        this.callbackUrl = callbackUrl;
        this.windowTitle = windowTitle;
        this.project = project;
    }

    public void browse() {
        LauncherTask task = new LauncherTask(project, windowTitle, true);
        task.queue();
    }

    private class LauncherTask extends Task.Modal {
        public LauncherTask(@Nullable Project project, @NotNull String title, boolean canBeCancelled) {
            super(project, title, canBeCancelled);
        }

        @Override
        public void run(@NotNull ProgressIndicator progressIndicator) {
            try {
                progressIndicator.setIndeterminate(true);

                // download the browser app jar
                progressIndicator.setText("Loading authentication components...");
                File appJar = ADJarLoader.load();

                // popup auth UI
                progressIndicator.setText("Signing in...");
                launch(appJar);
            } catch (ExecutionException e) {
                reportError(e);
            } catch (MalformedURLException e) {
                reportError(e);
            } catch (ClassNotFoundException e) {
                reportError(e);
            } catch (NoSuchMethodException e) {
                reportError(e);
            } catch (InvocationTargetException e) {
                reportError(e);
            } catch (IllegalAccessException e) {
                reportError(e);
            } catch (IOException e) {
                reportError(e);
            } catch (InterruptedException e) {
                reportError(e);
            }
        }

        private void launch(File appJar) throws NoSuchMethodException, InterruptedException, IOException, IllegalAccessException, InvocationTargetException, ClassNotFoundException {
            String osName = System.getProperty("os.name").toLowerCase();
            boolean isMac = osName.contains("mac");
            boolean isLinux = osName.contains("linux");

            // Launch the browser in-proc on Windows and out-proc on Mac and Linux.
            // We do this because:
            //  [1] On OSX, in order for SWT to work the -XstartOnFirstThread option must
            //      have been passed to the VM which unfortunately is not passed to IJ/AS
            //      when it is invoked. So we launch a new instance of the JVM with this
            //      option set.
            //  [2] On Linux, right now, launching SWT in-proc seems to cause intermittent
            //      crashes. So till we know why that's happening (will be fixed in a future
            //      SWT release perhaps?) we'll launch out-proc which appears to work well.
            //
            // You might wonder why we don't just launch out-proc on Windows as well. Turns
            // out, caching of AD auth session cookies are per-process. This means that
            // subsequent runs of the auth flow in the browser in the same process will
            // cause it to reuse session cookies which saves the user from having to enter
            // credentials over and over again.
            if(isMac || isLinux) {
                launchExternalProcess(appJar, isMac);
            } else {
                launchInvoke(appJar);
            }
        }

        private void launchExternalProcess(File appJar, boolean isMac) throws IOException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException, InterruptedException {
            List<String> args = new ArrayList<String>();

            // fetch path to the currently running JVM
            File javaHome = new File(System.getProperty("java.home"));
            File javaExecutable = new File(javaHome, "bin" + File.separator + "java");
            args.add(javaExecutable.getAbsolutePath());

            if(isMac) {
                // swt on mac requires this argument in order for the swt dispatch
                // loop to be running on the UI thread
                args.add("-XstartOnFirstThread");
            }
            args.add("-cp");
            args.add(appJar.getAbsolutePath());
            args.add("com.microsoftopentechnologies.adinteractiveauth.Program");
            args.add(url);
            args.add(redirectUrl);
            args.add(callbackUrl);
            args.add(windowTitle);
            // process should exit after sign in is complete
            args.add("true");

            ProcessBuilder pb = new ProcessBuilder(args);
            pb.start();
        }

        private void launchInvoke(File appJar) throws MalformedURLException, ClassNotFoundException, NoSuchMethodException, IllegalAccessException, InvocationTargetException {
            if(loader == null) {
                loader = new URLClassLoader(new URL[] {
                        new URL("file:///" + appJar.getPath())
                }, BrowserLauncher.class.getClassLoader());
            }

            Class<?> program = loader.loadClass("com.microsoftopentechnologies.adinteractiveauth.Program");
            final Method main = program.getDeclaredMethod("main", String[].class);
            final String[] args = new String[] {
                url, redirectUrl, callbackUrl, windowTitle
            };

            main.invoke(null, (Object) args);
        }

        private void reportError(Throwable err) {
            UIHelper.showException("An error occurred while loading authentication components.", err);
        }
    }
}
