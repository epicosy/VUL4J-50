/*
 * Copyright 2008-2012 by Emeric Vernat
 *
 *     This file is part of Java Melody.
 *
 * Java Melody is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Java Melody is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Java Melody.  If not, see <http://www.gnu.org/licenses/>.
 */
package net.bull.javamelody;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Method;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectInstance;
import javax.management.ObjectName;

import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;

import org.quartz.JobDetail;
import org.quartz.Scheduler;

/**
 * Énumération des actions possibles dans l'IHM.
 * @author Emeric Vernat
 * @author <a href="mailto:davidkarlsen@gmail.com">David J. M. Karlsen (IBM heapdump support)<a>
 */
enum Action { // NOPMD
	/**
	 * Test d'envoi du rapport pdf par mail.
	 */
	MAIL_TEST(""),
	/**
	 * Réinitialisation d'un compteur non périodique.
	 */
	CLEAR_COUNTER("http"),
	/**
	 * Garbage Collect.
	 */
	GC("systeminfo"),
	/**
	 * Invalidations des sessions http.
	 */
	INVALIDATE_SESSIONS("systeminfo"),
	/**
	 * Invalidation d'une session http.
	 */
	INVALIDATE_SESSION(""),
	/**
	 * Heap dump.
	 */
	HEAP_DUMP("systeminfo"),
	/**
	 * Purge le contenu de tous les caches (ie, for ALL_CACHE_MANAGERS {cacheManager.clearAll()})
	 */
	CLEAR_CACHES("caches"),
	/**
	 * Purge le contenu  d'un cache
	 */
	CLEAR_CACHE("caches"),
	/**
	 * Tue un thread java.
	 */
	KILL_THREAD("threads"),
	/**
	 * Met un job quartz en pause.
	 */
	PAUSE_JOB("jobs"),
	/**
	 * Enlève la pause d'un job quartz.
	 */
	RESUME_JOB("jobs"),
	/**
	 * Réinitialisation des hotspots.
	 */
	CLEAR_HOTSPOTS(""),
	/**
	 * Purge les fichiers .rrd et .ser.gz obsolètes.
	 */
	PURGE_OBSOLETE_FILES("bottom");

	static final String JAVA_VENDOR = System.getProperty("java.vendor");

	/**
	 * Booléen selon que l'action 'Garbage collector' est possible.
	 */
	static final boolean GC_ENABLED = !ManagementFactory.getRuntimeMXBean().getInputArguments()
			.contains("-XX:+DisableExplicitGC");
	/**
	 * Booléen selon que l'action 'Heap dump' est possible.
	 */
	static final boolean HEAP_DUMP_ENABLED = "1.6".compareTo(System.getProperty("java.version")) < 0
			&& (JAVA_VENDOR.contains("Sun") || JAVA_VENDOR.contains("Oracle") || JAVA_VENDOR
					.contains("IBM"));

	private static final String ALL = "all";

	/**
	 * Nom du contexte dans lequel est exécutée l'action
	 * (servira dans l'url pour replacer la page html sur l'anchor de même nom)
	 */
	private final String contextName;

	private Action(String contextName) {
		this.contextName = contextName;
	}

	String getContextName(String counterName) {
		if (this == CLEAR_COUNTER && !ALL.equalsIgnoreCase(counterName)) {
			return counterName;
		}
		return contextName;
	}

	/**
	 * Convertit le code d'une action en énumération de l'action.
	 * @param action String
	 * @return Action
	 */
	static Action valueOfIgnoreCase(String action) {
		return valueOf(action.toUpperCase(Locale.getDefault()).trim());
	}

	/**
	 * Vérifie que le paramètre pour activer les actions systèmes est positionné.
	 */
	static void checkSystemActionsEnabled() {
		if (!Parameters.isSystemActionsEnabled()) {
			throw new IllegalStateException(I18N.getString("Actions_non_activees"));
		}
	}

	/**
	 * Exécute l'action.
	 * @param collector Collector pour une réinitialisation et test de mail
	 * @param collectorServer Serveur de collecte pour test de mail (null s'il n'y en a pas)
	 * @param counterName Nom du compteur pour une réinitialisation
	 * @param sessionId Identifiant de session pour invalidation (null sinon)
	 * @param threadId Identifiant du thread sous la forme pid_ip_id
	 * @param jobId Identifiant du job sous la forme pid_ip_id
	 * @param cacheId Identifiant du cache à vider
	 * @return Message de résultat
	 * @throws IOException e
	 */
	// CHECKSTYLE:OFF
	String execute(Collector collector, CollectorServer collectorServer, String counterName, // NOPMD
			String sessionId, String threadId, String jobId, String cacheId) throws IOException {
		// CHECKSTYLE:ON
		String messageForReport;
		switch (this) {
		case CLEAR_COUNTER:
			assert collector != null;
			assert counterName != null;
			messageForReport = clearCounter(collector, counterName);
			break;
		case MAIL_TEST:
			assert collector != null;
			messageForReport = mailTest(collector, collectorServer);
			break;
		case GC:
			if (GC_ENABLED) {
				// garbage collector
				final long before = Runtime.getRuntime().totalMemory()
						- Runtime.getRuntime().freeMemory();
				gc();
				final long after = Runtime.getRuntime().totalMemory()
						- Runtime.getRuntime().freeMemory();
				messageForReport = I18N.getFormattedString("ramasse_miette_execute",
						(before - after) / 1024);
			} else {
				messageForReport = I18N.getString("ramasse_miette_desactive");
			}
			break;
		case HEAP_DUMP:
			if (HEAP_DUMP_ENABLED) {
				if (JAVA_VENDOR.contains("IBM")) {
					ibmHeapDump();
					messageForReport = I18N.getString("heap_dump_genere_ibm");
				} else {
					// heap dump à générer dans le répertoire temporaire sur le serveur
					// avec un suffixe contenant le host, la date et l'heure et avec une extension hprof
					// (utiliser jvisualvm du jdk ou MAT d'eclipse en standalone ou en plugin)
					final String heapDumpPath = heapDump().getPath();
					messageForReport = I18N.getFormattedString("heap_dump_genere",
							heapDumpPath.replace('\\', '/'));
				}
			} else {
				messageForReport = I18N.getString("heap_dump_not_good");
			}
			break;
		case INVALIDATE_SESSIONS:
			// invalidation des sessions http
			SessionListener.invalidateAllSessions();
			messageForReport = I18N.getString("sessions_http_invalidees");
			break;
		case INVALIDATE_SESSION:
			// invalidation d'une session http
			assert sessionId != null;
			SessionListener.invalidateSession(sessionId);
			messageForReport = I18N.getString("session_http_invalidee");
			break;
		case CLEAR_CACHES:
			clearCaches();
			messageForReport = I18N.getString("caches_purges");
			break;
		case CLEAR_CACHE:
			clearCache(cacheId);
			messageForReport = I18N.getFormattedString("cache_purge", cacheId);
			break;
		case KILL_THREAD:
			assert threadId != null;
			messageForReport = killThread(threadId);
			break;
		case PAUSE_JOB:
			assert jobId != null;
			messageForReport = pauseJob(jobId);
			break;
		case RESUME_JOB:
			assert jobId != null;
			messageForReport = resumeJob(jobId);
			break;
		case CLEAR_HOTSPOTS:
			assert collector.getSamplingProfiler() != null;
			collector.getSamplingProfiler().clear();
			messageForReport = I18N.getString("hotspots_cleared");
			break;
		case PURGE_OBSOLETE_FILES:
			assert collector != null;
			collector.deleteObsoleteFiles();
			messageForReport = I18N.getString("fichiers_obsoletes_purges") + '\n'
					+ I18N.getString("Usage_disque") + ": "
					+ (collector.getDiskUsage() / 1024 / 1024 + 1) + ' ' + I18N.getString("Mo");
			break;
		default:
			throw new IllegalStateException(toString());
		}
		if (messageForReport != null) {
			// log pour information en debug
			LOG.debug("Action '" + this + "' executed. Result: "
					+ messageForReport.replace('\n', ' '));
		}
		return messageForReport;
	}

	private String clearCounter(Collector collector, String counterName) {
		String messageForReport;
		if (ALL.equalsIgnoreCase(counterName)) {
			for (final Counter counter : collector.getCounters()) {
				collector.clearCounter(counter.getName());
			}
			messageForReport = I18N.getFormattedString("Toutes_statistiques_reinitialisees",
					counterName);
		} else {
			// l'action Réinitialiser a été appelée pour un compteur
			collector.clearCounter(counterName);
			messageForReport = I18N.getFormattedString("Statistiques_reinitialisees", counterName);
		}
		return messageForReport;
	}

	private String mailTest(Collector collector, CollectorServer collectorServer) {
		// note: a priori, inutile de traduire cela
		if (!HtmlAbstractReport.isPdfEnabled()) {
			throw new IllegalStateException("itext classes not found: add the itext dependency");
		}
		if (Parameters.getParameter(Parameter.MAIL_SESSION) == null) {
			throw new IllegalStateException(
					"mail-session has no value: add the mail-session parameter");
		}
		if (Parameters.getParameter(Parameter.ADMIN_EMAILS) == null) {
			throw new IllegalStateException(
					"admin-emails has no value: add the admin-emails parameter");
		}
		try {
			if (collectorServer == null) {
				// serveur local
				new MailReport().sendReportMailForLocalServer(collector, Period.JOUR);
			} else {
				// serveur de collecte
				new MailReport().sendReportMail(collector, true, collectorServer
						.getJavaInformationsByApplication(collector.getApplication()), Period.JOUR);
			}
		} catch (final Exception e) {
			throw new RuntimeException(e); // NOPMD
		}
		return "Mail sent with pdf report for the day to admins";
	}

	private File heapDump() throws IOException {
		final boolean gcBeforeHeapDump = true;
		try {
			final MBeanServer platformMBeanServer = ManagementFactory.getPlatformMBeanServer();
			final ObjectInstance instance = platformMBeanServer.getObjectInstance(new ObjectName(
					"com.sun.management:type=HotSpotDiagnostic"));
			final Object mxBean = platformMBeanServer.instantiate(instance.getClassName());
			final Object vmOption = ((com.sun.management.HotSpotDiagnosticMXBean) mxBean)
					.getVMOption("HeapDumpPath");
			final String heapDumpPath;
			if (vmOption == null) {
				heapDumpPath = null;
			} else {
				heapDumpPath = ((com.sun.management.VMOption) vmOption).getValue();
			}
			final String path;
			if (heapDumpPath == null || heapDumpPath.length() == 0) {
				path = Parameters.TEMPORARY_DIRECTORY.getPath();
			} else {
				// -XX:HeapDumpPath=/tmp par exemple a été spécifié comme paramètre de VM.
				// Dans ce cas, on prend en compte ce paramètre "standard" de la JVM Hotspot
				final File file = new File(heapDumpPath);
				if (file.exists()) {
					if (file.isDirectory()) {
						path = heapDumpPath;
					} else {
						path = file.getParent();
					}
				} else {
					if (!file.mkdirs()) {
						throw new IllegalStateException("Can't create directory " + file.getPath());
					}
					path = heapDumpPath;
				}
			}
			final DateFormat dateFormat = new SimpleDateFormat("yyyyMMdd_HHmmss",
					Locale.getDefault());
			final File heapDumpFile = new File(path, "heapdump-" + Parameters.getHostName() + '-'
					+ dateFormat.format(new Date()) + ".hprof");
			if (heapDumpFile.exists()) {
				try {
					// si le fichier existe déjà, un heap dump a déjà été généré dans la même seconde
					// donc on attends 1 seconde pour créer le fichier avec un nom différent
					Thread.sleep(1000);
				} catch (final InterruptedException e) {
					throw new IllegalStateException(e);
				}
				return heapDump();
			}
			((com.sun.management.HotSpotDiagnosticMXBean) mxBean).dumpHeap(heapDumpFile.getPath(),
					gcBeforeHeapDump);
			return heapDumpFile;
		} catch (final JMException e) {
			throw new IllegalStateException(e);
		}
	}

	private void ibmHeapDump() {
		try {
			final Class<?> dumpClass = getClass().getClassLoader().loadClass("com.ibm.jvm.Dump"); // NOPMD
			final Class<?>[] argTypes = null;
			final Method dump = dumpClass.getMethod("HeapDump", argTypes);
			final Object[] args = null;
			dump.invoke(null, args);
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	// cette méthode doit s'appeler "gc" pour que findbugs ne fasse pas de warning
	@SuppressWarnings("all")
	private void gc() {
		Runtime.getRuntime().gc();
	}

	@SuppressWarnings("unchecked")
	private void clearCaches() {
		final List<CacheManager> allCacheManagers = CacheManager.ALL_CACHE_MANAGERS;
		for (final CacheManager cacheManager : allCacheManagers) {
			cacheManager.clearAll();
		}
	}

	@SuppressWarnings("unchecked")
	private void clearCache(String cacheId) {
		final List<CacheManager> allCacheManagers = CacheManager.ALL_CACHE_MANAGERS;
		for (final CacheManager cacheManager : allCacheManagers) {
			final Cache cache = cacheManager.getCache(cacheId);
			if (cache != null) {
				cache.removeAll();
			}
		}
	}

	private String killThread(String threadId) {
		final String[] values = threadId.split("_");
		if (values.length != 3) {
			throw new IllegalArgumentException(threadId);
		}
		// rq : la syntaxe vérifiée ici doit être conforme à ThreadInformations.buildGlobalThreadId
		if (values[0].equals(PID.getPID()) && values[1].equals(Parameters.getHostAddress())) {
			final long myThreadId = Long.parseLong(values[2]);
			final List<Thread> threads = JavaInformations.getThreadsFromThreadGroups();
			for (final Thread thread : threads) {
				if (thread.getId() == myThreadId) {
					stopThread(thread);
					return I18N.getFormattedString("Thread_tue", thread.getName());
				}
			}
			return I18N.getString("Thread_non_trouve");
		}

		// cette action ne concernait pas cette JVM, donc on ne fait rien
		return null;
	}

	@SuppressWarnings("deprecation")
	private void stopThread(Thread thread) {
		// I know that it is unsafe and the user has been warned
		thread.stop();
	}

	private String pauseJob(String jobId) {
		if (ALL.equalsIgnoreCase(jobId)) {
			pauseAllJobs();
			return I18N.getString("all_jobs_paused");
		}

		final String[] values = jobId.split("_");
		if (values.length != 3) {
			throw new IllegalArgumentException(jobId);
		}
		// rq : la syntaxe vérifiée ici doit être conforme à JobInformations.buildGlobalJobId
		if (values[0].equals(PID.getPID()) && values[1].equals(Parameters.getHostAddress())) {
			if (pauseJobById(Integer.parseInt(values[2]))) {
				return I18N.getString("job_paused");
			}
			return I18N.getString("job_notfound");
		}

		// cette action ne concernait pas cette JVM, donc on ne fait rien
		return null;
	}

	private boolean pauseJobById(int myJobId) {
		try {
			for (final Scheduler scheduler : JobInformations.getAllSchedulers()) {
				for (final JobDetail jobDetail : JobInformations.getAllJobsOfScheduler(scheduler)) {
					if (QuartzAdapter.getSingleton().getJobFullName(jobDetail).hashCode() == myJobId) {
						QuartzAdapter.getSingleton().pauseJob(jobDetail, scheduler);
						return true;
					}
				}
			}
			return false;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void pauseAllJobs() {
		try {
			for (final Scheduler scheduler : JobInformations.getAllSchedulers()) {
				scheduler.pauseAll();
			}
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private String resumeJob(String jobId) {
		if (ALL.equalsIgnoreCase(jobId)) {
			resumeAllJobs();
			return I18N.getString("all_jobs_resumed");
		}
		final String[] values = jobId.split("_");
		if (values.length != 3) {
			throw new IllegalArgumentException(jobId);
		}
		// rq : la syntaxe vérifiée ici doit être conforme à JobInformations.buildGlobalJobId
		if (values[0].equals(PID.getPID()) && values[1].equals(Parameters.getHostAddress())) {
			if (resumeJobById(Integer.parseInt(values[2]))) {
				return I18N.getString("job_resumed");
			}
			return I18N.getString("job_notfound");
		}

		// cette action ne concernait pas cette JVM, donc on ne fait rien
		return null;
	}

	private boolean resumeJobById(int myJobId) {
		try {
			for (final Scheduler scheduler : JobInformations.getAllSchedulers()) {
				for (final JobDetail jobDetail : JobInformations.getAllJobsOfScheduler(scheduler)) {
					if (QuartzAdapter.getSingleton().getJobFullName(jobDetail).hashCode() == myJobId) {
						QuartzAdapter.getSingleton().resumeJob(jobDetail, scheduler);
						return true;
					}
				}
			}
			return false;
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private void resumeAllJobs() {
		try {
			for (final Scheduler scheduler : JobInformations.getAllSchedulers()) {
				scheduler.resumeAll();
			}
		} catch (final Exception e) {
			throw new IllegalStateException(e);
		}
	}
}
