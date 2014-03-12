package org.projectodd.wunderboss.scheduling;

import org.jboss.logging.Logger;
import org.projectodd.wunderboss.Options;
import org.quartz.CronScheduleBuilder;
import org.quartz.JobBuilder;
import org.quartz.JobDataMap;
import org.quartz.JobDetail;
import org.quartz.JobKey;
import org.quartz.Scheduler;
import org.quartz.SchedulerException;
import org.quartz.Trigger;
import org.quartz.TriggerBuilder;
import org.quartz.impl.DirectSchedulerFactory;

import java.util.HashMap;
import java.util.Map;

public class QuartzScheduling implements Scheduling<Scheduler> {
    /*
     options: jobstore? threadpool? other scheduler opts?
     */
    public QuartzScheduling(String name, Options options) {
        this.name = name;
        this.numThreads = options.getInt("num_threads", 5);
    }

    @Override
    public void start() {
        System.setProperty("org.terracotta.quartz.skipUpdateCheck", "true");
        DirectSchedulerFactory factory = DirectSchedulerFactory.getInstance();
        try {
            if (!started) {
                factory.createVolatileScheduler(numThreads);
                this.scheduler = factory.getScheduler();
                this.scheduler.start();
                started = true;
                log.info("Quartz started");
            }
        } catch (SchedulerException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void stop() {
        if (started) {
            try {
               this.scheduler.shutdown(true);
            }  catch (SchedulerException e) {

                // TODO: something better
                e.printStackTrace();
            }
            started = false;
            log.info("Quartz stopped");
        }
    }

    @Override
    public Scheduler implementation() {
        return this.scheduler;
    }

    @Override
    public String name() {
        return this.name;
    }

    @Override
    public synchronized boolean schedule(String name, Runnable fn, Map<String, Object> opts) throws SchedulerException {
        Options options = new Options(opts);
        String cronString = options.getString("cron");
        // Cast and retrieve this here to error early if it's not given
        //TODO: unschedule existing job, returning true
        start();
        JobDataMap jobDataMap = new JobDataMap();
        // TODO: Quartz says only serializable things should be in here
        jobDataMap.put(RunnableJob.RUN_FUNCTION_KEY, fn);
        JobDetail job = JobBuilder.newJob(RunnableJob.class)
                .usingJobData(jobDataMap)
                .build();
        Trigger trigger = TriggerBuilder.newTrigger()
                .startNow()
                .withSchedule(CronScheduleBuilder.cronSchedule(cronString))
                .build();

        this.scheduler.scheduleJob(job, trigger);

        this.currentJobs.put("name", job.getKey());

        return false;
    }

    @Override
    public synchronized boolean unschedule(String name) throws SchedulerException {
        if (currentJobs.containsKey(name)) {
            this.scheduler.deleteJob(currentJobs.get(name));

            return true;
        }

        return false;
    }

    private final String name;
    private int numThreads;
    private boolean started;
    private Scheduler scheduler;
    private final Map<String, JobKey> currentJobs = new HashMap<>();

    private static final Logger log = Logger.getLogger(Scheduling.class);
}
