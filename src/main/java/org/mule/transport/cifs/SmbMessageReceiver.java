/*
 * $Id$
 * --------------------------------------------------------------------------------------
 * Copyright (c) MuleSoft, Inc.  All rights reserved.  http://www.mulesoft.com
 *
 * The software in this package is published under the terms of the CPAL v1.0
 * license, a copy of which has been included with this distribution in the
 * LICENSE.txt file.
 */

package org.mule.transport.cifs;

import jcifs.smb.SmbFile;
import org.mule.api.MessagingException;
import org.mule.api.MuleEvent;
import org.mule.api.MuleMessage;
import org.mule.api.construct.FlowConstruct;
import org.mule.api.endpoint.EndpointURI;
import org.mule.api.endpoint.InboundEndpoint;
import org.mule.api.execution.ExecutionCallback;
import org.mule.api.execution.ExecutionTemplate;
import org.mule.api.lifecycle.CreateException;
import org.mule.api.transport.Connector;
import org.mule.execution.TransactionalErrorHandlingExecutionTemplate;
import org.mule.transport.AbstractPollingMessageReceiver;
import org.mule.util.StringUtils;

import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * <code>SmbMessageReceiver</code> TODO document
 */
public class SmbMessageReceiver extends AbstractPollingMessageReceiver {
    private String moveToDir = "";
    private String moveToPattern = "";
    private long fileAge = 0;
    protected final SmbConnector smbConnector;
    protected final FilenameFilter filenameFilter;
    protected final String smbPath;

    public SmbMessageReceiver(Connector connector, FlowConstruct flowConstruct, InboundEndpoint endpoint,
                              long frequency, String moveToDir, String moveToPattern, long fileAge) throws CreateException {
        super(connector, flowConstruct, endpoint);

        this.setFrequency(frequency);
        this.moveToDir = moveToDir;
        this.moveToPattern = moveToPattern;
        this.fileAge = fileAge;

        this.smbConnector = (SmbConnector) connector;

        if (endpoint.getFilter() instanceof FilenameFilter) {
            this.filenameFilter = (FilenameFilter) endpoint.getFilter();
        } else {
            this.filenameFilter = null;
        }

        EndpointURI uri = endpoint.getEndpointURI();

        if (StringUtils.isBlank(uri.getUser()) || StringUtils.isBlank(uri.getPassword())) {
            logger.warn("No user or password supplied. Attempting to connect with just smb://<host>/<path>");
            logger.warn("smb://" + uri.getHost() + uri.getPath());
            smbPath = "smb://" + uri.getHost() + uri.getPath();
        } else {
            smbPath = "smb://" + uri.getUser() + ":" + uri.getPassword() + "@" + uri.getHost()
                    + uri.getPath();
        }
    }

    @Override
    public void poll() {
        try {
            SmbFile[] files = listFiles();
            if (logger.isDebugEnabled()) {
                logger.debug("Poll encountered " + files.length + " new file(s)");
            }
            for (SmbFile file : files) {
                if (!getLifecycleState().isStopping()) {
                    processFile(file);
                }
            }
        } catch (Exception e) {
            getConnector().getMuleContext().getExceptionListener().handleException(e);
        }


    }

    protected SmbFile[] listFiles() throws Exception {
        SmbFile[] files = new SmbFile(smbPath).listFiles();
        if (files == null || files.length == 0) {
            return files;
        }

        List<SmbFile> filesToProcess = new ArrayList<SmbFile>();
        for (SmbFile file : files) {
            if (file.isFile()) {
                if (filenameFilter == null || filenameFilter.accept(null, file.getName())) {
                    filesToProcess.add(file);
                }
            }
        }

        return filesToProcess.toArray(new SmbFile[filesToProcess.size()]);
    }

    protected void processFile(SmbFile file) throws Exception {
        if (!smbConnector.checkFileAge(file, fileAge)) {
            return;
        }

        final MuleMessage message = createMuleMessage(file);


        try {
            createExecutionTemplate().execute(new ExecutionCallback<MuleEvent>() {
                @Override
                public MuleEvent process() throws Exception {
                    return routeMessage(message);
                }
            });
            postProcess(file, message);
        } catch (MessagingException e) {
            if (!e.causedRollback()) {
                postProcess(file, message);
            }
        } catch (Exception e) {
            connector.getMuleContext().getExceptionListener().handleException(e);
        }
    }

    protected void postProcess(SmbFile file, MuleMessage message) throws Exception {
        if (!StringUtils.isEmpty(moveToDir)) {
            String destinationFileName = file.getName();

            if (!StringUtils.isEmpty(moveToPattern)) {
                destinationFileName = (smbConnector).getFilenameParser().getFilename(message, moveToPattern);
            }

            SmbFile dest;
            EndpointURI uri = endpoint.getEndpointURI();

            if (SmbConnector.checkNullOrBlank(uri.getUser())
                    || SmbConnector.checkNullOrBlank(uri.getPassword())) {
                dest = new SmbFile("smb://" + uri.getHost() + moveToDir + destinationFileName);
            } else {
                dest = new SmbFile("smb://" + uri.getUser() + ":" + uri.getPassword() + "@" + uri.getHost()
                        + moveToDir + destinationFileName);
            }

            logger.debug("dest: " + dest);

            try {
                file.renameTo(dest);
            } catch (Exception e) {
                throw new IOException("Failed to rename file " + file.getName() + " to " + dest.getName() +
                        ". Smb error! " + e.getMessage(), e);
            }

            logger.debug("Renamed processed file " + file.getName() + " to " + moveToDir
                    + destinationFileName);
        } else {
            try {
                file.delete();
            } catch (Exception e) {
                throw new IOException("Failed to delete file " + file.getName() + ". Smb error: " + e.getMessage(), null);
            }

            if (logger.isDebugEnabled()) {
                logger.debug("Deleted processed file " + file.getName());
            }
        }
    }

    @Override
    protected boolean pollOnPrimaryInstanceOnly() {
        return true;
    }

    @Override
    protected void doConnect() throws Exception {
        // no op
    }

    protected ExecutionTemplate<MuleEvent> createExecutionTemplate() {
        return TransactionalErrorHandlingExecutionTemplate.createMainExecutionTemplate(endpoint.getMuleContext(), endpoint.getTransactionConfig());
    }
}
