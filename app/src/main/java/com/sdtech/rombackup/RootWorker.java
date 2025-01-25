package com.sdtech.rombackup;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class RootWorker {
    
    private static boolean rootFound = false;
    private static boolean rootGranted = false;

    public static boolean rootFound() {
        try {
            ExecutorService executor = Executors.newSingleThreadExecutor();
            rootFound = false;
            //paths for searching su binary.
            String[] paths = "/debug_ramdisk/su /sbin/su /system/sbin/su /system/bin/su /system/xbin/su /su/bin/su /magisk/.core/bin/su".split(" ");

            Future<Boolean> result = executor.submit(() -> {
                for (String path : paths) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(Runtime.getRuntime().exec("ls " + path).getInputStream()));
                        String line = reader.readLine();
                        if (line != null && line.startsWith(path)) {
                            rootFound = true;
                        }
                        reader.close();
                    } catch (Exception err) {

                    }
                }

                /** trying second method */
                if (!rootFound) {
                    for (String path : paths) {
                        if (new File(path).exists()) {
                            rootFound = true;
                        }
                    }
                }
            }, true);
            if (result.get()) {
                executor.shutdown();
            }

            return rootFound;
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean rootGranted() {
        if (!rootFound()) {
            return false;
        }
        try {
            rootGranted = false;
            final List<String> output = RootShell.command("id").getOutput();
            for (String out : output) {
                if (out.contains("uid=0")) {
                    rootGranted = true;
                }
            }
            // request root permission
            if (!rootGranted) {
                try {
                    closeShell();
                    RootShell shell =  RootShell.command("su","-c","/system/bin/sh");
                    shell.addCommand("echo ROOTGRANTED");
                    List<String> shelloutput = shell.getOutput();
                    for(String out : shelloutput) {
                    	if(out.matches("ROOTGRANTED")) {
                    		rootGranted=true;
                    	}
                    }
                } catch (Exception ignored) {}
            }
            return rootGranted;
        } catch (Exception e) {
            return false;
        }
    }
    
    public static RootShell command(String... commands){
        return RootShell.command(commands);
    }
    
    public static void closeShell() {
    	RootShell.closeShell();
    }
    
    public static class RootShell {
        
        private OutputStream cmdOutput;
        private InputStream  cmdInput;
        private InputStream  cmdError;
        
        private static RootShell mShell;
        
        private RootShell(Process prs){
            cmdOutput = prs.getOutputStream();
            cmdInput  = prs.getInputStream();
            cmdError  = prs.getErrorStream();
        }
        
        private void wipeStream(InputStream in) {
        	try {
                int available = 0;
                while ((available=in.available()) != 0){
                    in.skip(available);
                }
            } catch (IOException ignored) {}
        }
        
        public static RootShell command(String... commands){
            if(mShell == null) {
            	try {
            		Process prs = new ProcessBuilder(commands).start();
                    mShell = new RootShell(prs);
            	} catch(Exception ignored) {}
            }else{
                mShell.addCommand(commands);
            }
            return mShell;
        }
        
        public void addCommand(String... commands) {
        	try {
        		if(cmdOutput==null){
                    command("/system/bin/sh");
                }
                wipeStream(cmdInput);
                wipeStream(cmdError);
                for(String cmd : commands){
                    cmdOutput.write((cmd+"\n").getBytes());
                    cmdOutput.flush();
                    cmdOutput.write("echo ENDOFCOMMAND\n".getBytes());
                    cmdOutput.flush();
                }
        	} catch(Exception ignored) {}
        }
        
        private void close() {
        	if(mShell!=null){
                try {
                    mShell=null;
                	cmdOutput.close();
                    cmdInput.close();
                    cmdError.close();
                } catch(Exception ignored) {}
            }
        }
        
        private static void closeShell() {
        	mShell.close();
        }
        
        public List<String> getOutput() throws Exception{
            final ExecutorService executor = Executors.newSingleThreadExecutor();
            final List<String> output = new ArrayList<>();
            Future outResult = executor.submit(()->{
                try {
                	BufferedReader outputReader = new BufferedReader(new InputStreamReader(cmdInput));
                    String line;
                    while((line = outputReader.readLine()) != null) {
                        if(!line.matches("ENDOFCOMMAND")) {
                        	output.add(line);
                        } else {
                            break;
                        }
                    }
                } catch(Exception ignored) {}
            });
            outResult.get();
            executor.shutdown();
        	return output;
        }
        
        public List<String> getError() throws Exception{
            if(cmdError == null) {
            	return new ArrayList<>();
            }
            ExecutorService executor = Executors.newSingleThreadExecutor();
            final List<String> errors = new ArrayList<>();
            Future errResult = executor.submit(()->{
                try {
                	BufferedReader errorReader = new BufferedReader(new InputStreamReader(cmdError));
                    String line;
                    while((line=errorReader.readLine()) != null) {
                        errors.add(line);
                    }
                } catch(Exception ignored) {}
            });
            errResult.get();
            executor.shutdown();
        	return errors;
        }
    }
}
