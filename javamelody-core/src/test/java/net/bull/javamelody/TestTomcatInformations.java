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

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;
import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import javax.management.JMException;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.servlet.ServletContext;

import org.junit.Before;
import org.junit.Test;

/**
 * Test unitaire de la classe TomcatInformations.
 * @author Emeric Vernat
 */
public class TestTomcatInformations {
	/**
	 * Implémentation du MBean ThreadPool.
	 * @author Emeric Vernat
	 */
	public static class ThreadPool implements ThreadPoolMBean {
		/** {@inheritDoc} */
		@Override
		public int getmaxThreads() {
			return 200;
		}

		/** {@inheritDoc} */
		@Override
		public int getcurrentThreadsBusy() {
			return 22;
		}

		/** {@inheritDoc} */
		@Override
		public int getcurrentThreadCount() {
			return 42;
		}

		/** {@inheritDoc} */
		@Override
		public String[] getdummy() {
			return new String[] { "1", "2" };
		}

		/** {@inheritDoc} */
		@Override
		public Object gettoStringException() {
			return new Object() {
				@Override
				/** {@inheritDoc} */
				public String toString() {
					throw new IllegalStateException("test");
				}
			};
		}

		/** {@inheritDoc} */
		@Override
		public int[] getintArrayAsInJRockit() {
			return new int[] { 1 };
		}

		/** {@inheritDoc} */
		@Override
		public double[] getdoubleArrayAsInJRockit() {
			return new double[] { 1d };
		}
	}

	/**
	 * Interface du MBean ThreadPool.
	 * @author Emeric Vernat
	 */
	public interface ThreadPoolMBean {
		/**
		 * attribut maxThreads.
		 * @return int
		 */
		int getmaxThreads();

		/**
		 * attribut currentThreadsBusy.
		 * @return int
		 */
		int getcurrentThreadsBusy();

		/**
		 * attribut currentThreadCount.
		 * @return int
		 */
		int getcurrentThreadCount();

		/**
		 * attribut dummy.
		 * @return String[]
		 */
		String[] getdummy();

		/**
		 * attribut toStringException.
		 * @return Object
		 */
		Object gettoStringException();

		/**
		 * @return int[]
		 */
		int[] getintArrayAsInJRockit();

		/**
		 * @return double[]
		 */
		double[] getdoubleArrayAsInJRockit();
	}

	/**
	 * Implémentation du MBean GlobalRequestProcessor.
	 * @author Emeric Vernat
	 */
	public static class GlobalRequestProcessor implements GlobalRequestProcessorMBean {
		private int requestCount = 100;

		/** {@inheritDoc} */
		@Override
		public long getbytesReceived() {
			return 0;
		}

		/** {@inheritDoc} */
		@Override
		public long getbytesSent() {
			return 100000;
		}

		/** {@inheritDoc} */
		@Override
		public int getrequestCount() {
			return requestCount;
		}

		/**
		 * setter.
		 * @param count int
		 */
		public void setrequestCount(int count) {
			this.requestCount = count;
		}

		/** {@inheritDoc} */
		@Override
		public int geterrorCount() {
			return 1;
		}

		/** {@inheritDoc} */
		@Override
		public long getprocessingTime() {
			return 2000;
		}

		/** {@inheritDoc} */
		@Override
		public long getmaxTime() {
			return 10000;
		}
	}

	/**
	 * Interface du MBean GlobalRequestProcessor.
	 * @author Emeric Vernat
	 */
	public interface GlobalRequestProcessorMBean {
		/**
		 * attribut bytesReceived.
		 * @return int
		 */
		long getbytesReceived();

		/**
		 * attribut bytesSent.
		 * @return int
		 */
		long getbytesSent();

		/**
		 * attribut requestCount.
		 * @return int
		 */
		int getrequestCount();

		/**
		 * attribut errorCount.
		 * @return int
		 */
		int geterrorCount();

		/**
		 * attribut processingTime.
		 * @return int
		 */
		long getprocessingTime();

		/**
		 * attribut maxTime.
		 * @return int
		 */
		long getmaxTime();
	}

	/** Test. */
	@Before
	public void setUp() {
		Utils.initialize();
		Utils.setProperty(Parameter.SYSTEM_ACTIONS_ENABLED, Boolean.TRUE.toString());
	}

	/** Test.
	 * @throws JMException e */
	@Test
	public void testTomcatInformations() throws JMException {
		System.setProperty("catalina.home", "unknown");
		// ce premier appel crée un MBeanServer
		TomcatInformations.initMBeans();
		assertNotNull("buildTomcatInformationsList",
				TomcatInformations.buildTomcatInformationsList());
		final MBeanServer mBeanServer = MBeans.getPlatformMBeanServer();
		final List<ObjectName> mBeans = new ArrayList<ObjectName>();
		mBeans.add(mBeanServer.registerMBean(new ThreadPool(),
				new ObjectName("Catalina:type=ThreadPool,name=http-8080")).getObjectName());
		TomcatInformations.initMBeans();
		try {
			// les appels suivants réutilise le MBeanServer créé
			assertNotNull("buildTomcatInformationsList",
					TomcatInformations.buildTomcatInformationsList());
			mBeans.add(mBeanServer.registerMBean(new GlobalRequestProcessor(),
					new ObjectName("Catalina:type=GlobalRequestProcessor,name=http-8080"))
					.getObjectName());
			TomcatInformations.initMBeans();
			assertNotNull("buildTomcatInformationsList",
					TomcatInformations.buildTomcatInformationsList());
			for (final TomcatInformations tomcatInformations : TomcatInformations
					.buildTomcatInformationsList()) {
				tomcatInformations.toString();
			}

			final Counter counter = new Counter("http", null);
			final Collector collector = new Collector("test", Arrays.asList(counter));
			final ServletContext context = createNiceMock(ServletContext.class);
			expect(context.getServerInfo()).andReturn("Mock").anyTimes();
			expect(context.getMajorVersion()).andReturn(3).anyTimes();
			expect(context.getMinorVersion()).andReturn(0).anyTimes();
			expect(context.getContextPath()).andReturn("/test").anyTimes();
			replay(context);
			Parameters.initialize(context);
			collector.collectLocalContextWithoutErrors();
			verify(context);
		} finally {
			for (final ObjectName registeredMBean : mBeans) {
				mBeanServer.unregisterMBean(registeredMBean);
			}
			TomcatInformations.initMBeans();
		}
	}
}
