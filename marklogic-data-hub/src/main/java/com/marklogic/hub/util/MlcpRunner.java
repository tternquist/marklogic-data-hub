/*
 * Copyright 2012-2016 MarkLogic Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.marklogic.hub.util;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.marklogic.client.DatabaseClient;
import com.marklogic.contentpump.bean.MlcpBean;
import com.marklogic.hub.HubConfig;
import com.marklogic.hub.flow.Flow;
import com.marklogic.hub.flow.FlowStatusListener;
import com.marklogic.hub.job.Job;
import com.marklogic.hub.job.JobManager;
import com.marklogic.hub.job.JobStatus;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

public class MlcpRunner extends ProcessRunner {

    private JobManager jobManager;
    private Flow flow;
    private JsonNode mlcpOptions;
    private String jobId = UUID.randomUUID().toString();
    private AtomicLong successfulEvents = new AtomicLong(0);
    private AtomicLong failedEvents = new AtomicLong(0);
    FlowStatusListener flowStatusListener;
    private String mlcpPath;
    private String mainClass;
    private DatabaseClient databaseClient;

    public MlcpRunner(String mlcpPath, String mainClass, HubConfig hubConfig, Flow flow, DatabaseClient databaseClient, JsonNode mlcpOptions, FlowStatusListener statusListener) {
        super();

        this.withHubconfig(hubConfig);

        this.jobManager = new JobManager(hubConfig.newJobDbClient());
        this.flowStatusListener = statusListener;
        this.flow = flow;
        this.mlcpOptions = mlcpOptions;
        this.mlcpPath = mlcpPath;
        this.mainClass = mainClass;
        this.databaseClient = databaseClient;
    }

    public String getJobId() {
        return jobId;
    }

    // TODO: add destination database here
    @Override
    public void run() {
        HubConfig hubConfig = getHubConfig();

        Job job = Job.withFlow(flow)
            .withJobId(jobId);
        jobManager.saveJob(job);

        try {
            MlcpBean bean = new ObjectMapper().readerFor(MlcpBean.class).readValue(mlcpOptions);
            bean.setHost(databaseClient.getHost());
            bean.setPort(databaseClient.getPort());

            // Assume that the HTTP credentials will work for mlcp
            bean.setUsername(hubConfig.getUsername());
            bean.setPassword(hubConfig.getPassword());

            File file = new File(mlcpOptions.get("input_file_path").asText());
            String canonicalPath = file.getCanonicalPath();
            bean.setInput_file_path(canonicalPath);
            bean.setTransform_param("\"" + bean.getTransform_param().replaceAll("\"", "") + ",jobId=" + jobId + "\"");

            buildCommand(bean);

            super.run();

            if (flowStatusListener != null) {
                flowStatusListener.onStatusChange(jobId, 100, "");
            }

        } catch (Exception e) {
            job.withStatus(JobStatus.FAILED)
                .withEndTime(new Date());
            jobManager.saveJob(job);
            throw new RuntimeException(e);
        } finally {
            JobStatus status;
            if (failedEvents.get() > 0 && successfulEvents.get() > 0) {
                status = JobStatus.FINISHED_WITH_ERRORS;
            }
            else if (failedEvents.get() == 0 && successfulEvents.get() > 0) {
                status = JobStatus.FINISHED;
            }
            else {
                status = JobStatus.FAILED;
            }

            // store the thing in MarkLogic
            job.withJobOutput(getProcessOutput())
                .withStatus(status)
                .setCounts(successfulEvents.get(), failedEvents.get(), 0, 0)
                .withEndTime(new Date());
            jobManager.saveJob(job);
        }
    }

    private String buildLoggerconfig() {
        return "<configuration>\n" +
            "\n" +
            "  <appender name=\"STDOUT\" class=\"ch.qos.logback.core.ConsoleAppender\">\n" +
            "    <!-- encoders are assigned the type\n" +
            "         ch.qos.logback.classic.encoder.PatternLayoutEncoder by default -->\n" +
            "    <encoder>\n" +
            "      <pattern>%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>\n" +
            "    </encoder>\n" +
            "  </appender>\n" +
            "\n" +
            "  <logger name=\"org.apache.http\" level=\"WARN\"/>\n" +
            "\n" +
            "  <logger name=\"com.marklogic.spring.batch.core.repository.dao.MarkLogicStepExecutionDao\" level=\"WARN\"/>\n" +
            "  <logger name=\"com.marklogic.spring.batch.core.repository.dao.MarkLogicJobExecutionDao\" level=\"WARN\"/>\n" +
            "  <logger name=\"com.marklogic.client.impl.DocumentManagerImpl\" level=\"WARN\"/>\n" +
            "  <logger name=\"com.marklogic.client.impl.DatabaseClientImpl\" level=\"WARN\"/>\n" +
            "  <logger name=\"com.marklogic\" level=\"INFO\"/>\n" +
            "  <logger name=\"com.marklogic.appdeployer\" level=\"INFO\"/>\n" +
            "  <logger name=\"com.marklogic.hub\" level=\"INFO\"/>\n" +
            "  <logger name=\"com.marklogic.contentpump\" level=\"INFO\"/>\n" +
            "  <logger name=\"org.apache.catalina.webresources.Cache\" level=\"ERROR\"/>\n" +
            "  <logger name=\"org.apache.hadoop.util.Shell\" level=\"OFF\"/>\n" +
            "  <logger name=\"org.apache.hadoop.util.NativeCodeLoader\" level=\"ERROR\"/>\n" +
            "\n" +
            "  <root level=\"WARN\">\n" +
            "    <appender-ref ref=\"STDOUT\" />\n" +
            "  </root>\n" +
            "</configuration>\n";
    }

    private void buildCommand(MlcpBean bean) throws IOException, InterruptedException {
        ArrayList<String> args = new ArrayList<>();
        if (this.mlcpPath != null && this.mlcpPath.length() > 0) {
            File mlcpFile = new File(this.mlcpPath);
            if (!mlcpFile.exists()) {
                throw new RuntimeException("MLCP does not exist at: " + mlcpPath);
            }
            else if (!mlcpFile.canExecute()) {
                throw new RuntimeException("Cannot execute: " + mlcpPath);
            }
            args.add(this.mlcpPath);
        }
        else {
            String javaHome = System.getProperty("java.home");
            String javaBin = javaHome +
                File.separator + "bin" +
                File.separator + "java";
            String classpath = System.getProperty("java.class.path");

            File loggerFile = File.createTempFile("mlcp-", "-logger.xml");
            FileUtils.writeStringToFile(loggerFile, buildLoggerconfig());

            args.add(javaBin);
            args.add("-Dlogback.configurationFile=" + loggerFile.toURI());
            if (classpath.endsWith(".war")) {
                args.add("-jar");
                args.add(classpath);
                args.add("mlcp");
            }
            else {
                args.add("-cp");
                args.add(classpath);
                args.add(mainClass);
            }
        }

        args.addAll(Arrays.asList(bean.buildArgs()));

        this.withArgs(args);

        this.withStreamConsumer(new MlcpConsumer(successfulEvents,
            failedEvents, flowStatusListener, jobId));
    }
}
