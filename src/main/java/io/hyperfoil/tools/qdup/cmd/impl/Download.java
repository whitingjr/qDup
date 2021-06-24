package io.hyperfoil.tools.qdup.cmd.impl;

import io.hyperfoil.tools.qdup.Host;
import io.hyperfoil.tools.qdup.Local;
import io.hyperfoil.tools.qdup.cmd.Cmd;
import io.hyperfoil.tools.qdup.cmd.Context;

import java.io.File;
import java.util.function.Supplier;

public class Download extends Cmd {
    private String path;
    private String destination;
    private Long maxSize;

    public Download(String path, String destination, Long maxSize ){
        this.path = path;
        this.destination = destination;
        if(maxSize != null && maxSize < 0) {
            this.maxSize = null;
        } else {
            this.maxSize = maxSize;
        }
    }
    public Download(String path, String destination ){
        this(path,destination, null);
    }
    public Download(String path){
        this(path,"", null);
    }
    public Download(String path, Long maxSize){
        this(path,"", maxSize);
    }
    public String getPath(){return path;}
    public String getDestination(){return destination;}
    public Long getMaxSize(){ return maxSize;}
    @Override
    public void run(String input, Context context) {

        execute(context, () -> context.getLocal(), () -> context.getHost());

        context.next(path);
    }

    @Override
    public Cmd copy() {
        return new Download(this.path,this.destination, this.maxSize);
    }
    @Override
    public String toString(){return "download: "+path+" "+destination;}

    public void execute(Context context, Supplier<Local> localProvider, Supplier<Host> hostSupplier){
        Local local = localProvider.get();
        Host host = hostSupplier.get();

        String remotePath = context == null ? path : populateStateVariables(path,this,context);
        String basePath = context == null ? null : context.getRunOutputPath()+ File.separator+context.getSession().getHost().getHostName();
        String destinationPath =  context == null ? destination : populateStateVariables(basePath + File.separator +destination,this,context);
        File destinationFile = new File(destinationPath);
        if(!destinationFile.exists()){
            destinationFile.mkdirs();
        }
        boolean canDownload = true;
        if(maxSize != null){
            Long remoteFileSize = local.remoteFileSize(remotePath,host);
            if(remoteFileSize > maxSize){
                canDownload = false;
                logger.warn("Download File: `{}`; is larger {} than max size: {} bytes", remotePath, remoteFileSize, maxSize);
            }
        }
        if(canDownload) {
            boolean worked = local.download(remotePath, destinationPath, host);
            if(!worked){
                if(context!=null) {
                    context.error("failed to download " + remotePath + " to " + destinationPath);
                    context.abort(false);
                }
            }
        }
    }

}
