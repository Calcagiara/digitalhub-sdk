package it.smartcommunitylabdhub.core.components.pollers;

import it.smartcommunitylabdhub.core.components.workflows.factory.Workflow;
import it.smartcommunitylabdhub.core.exceptions.StopPoller;
import lombok.extern.log4j.Log4j2;
import org.springframework.core.task.TaskExecutor;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Log4j2
public class Poller implements Runnable {
    private final List<Workflow> workflowList;
    private final ScheduledExecutorService scheduledExecutorService;
    private final long delay;
    private final boolean reschedule;
    private final String name;
    private boolean active;

    public Poller(String name, List<Workflow> workflowList, long delay, boolean reschedule, TaskExecutor executor) {
        this.name = name;
        this.workflowList = workflowList;
        this.delay = delay;
        this.reschedule = reschedule;
        this.active = true;
        this.scheduledExecutorService = (executor instanceof ScheduledExecutorService)
                ? (ScheduledExecutorService) executor
                : Executors.newSingleThreadScheduledExecutor();
    }

    ScheduledExecutorService getScheduledExecutor() {
        return this.scheduledExecutorService;
    }


    public void startPolling() {
        log.info("Poller [" + name + "] start: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
        getScheduledExecutor().schedule(this, delay, TimeUnit.SECONDS);
    }

    @Override
    public void run() {
        log.info("Poller [" + name + "] run: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");

        for (Workflow workflow : workflowList) {
            if (!active) {
                break;
            }
            executeWorkflow(workflow);
        }

        if (reschedule && active) {
            log.info("Poller [" + name + "] reschedule: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
            log.info("--------------------------------------------------------------");

            // Delay the rescheduling to ensure all workflows have completed
            getScheduledExecutor().schedule(this::startPolling, delay, TimeUnit.SECONDS);
        }

        // if not reschedule but still active can stop immediately only one iteration.
        if (!reschedule && active) {
            stopPolling();
        }

    }

    private void executeWorkflow(Workflow workflow) {
        try {
            log.info("Workflow execution: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
            workflow.execute(null);
        } catch (Exception e) {
            if (e instanceof StopPoller) {
                log.info("POLLER: " + e.getMessage());
                stopPolling(); // Stop this Poller thread.
            } else {
                log.error("POLLER EXCEPTION: " + e.getMessage());
                stopPolling();
            }
        }
    }

    public void stopPolling() {
        if (active) {
            active = false;
            log.info("Poller [" + name + "] stop: " + Thread.currentThread().getName() + " (ID: " + Thread.currentThread().getId() + ")");
            getScheduledExecutor().shutdown();
            try {
                if (!getScheduledExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                    getScheduledExecutor().shutdownNow();
                    if (!getScheduledExecutor().awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("Unable to shutdown executor service :(");
                    }
                }
            } catch (InterruptedException e) {
                getScheduledExecutor().shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
    }
}
