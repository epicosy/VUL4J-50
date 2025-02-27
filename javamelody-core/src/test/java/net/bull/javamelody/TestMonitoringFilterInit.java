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
package net.bull.javamelody; // NOPMD

import static org.easymock.EasyMock.createNiceMock;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.replay;
import static org.easymock.EasyMock.verify;

import java.io.IOException;
import java.lang.reflect.Field;

import javax.servlet.FilterChain;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.junit.Before;
import org.junit.Test;

/**
 * Test unitaire de la classe MonitoringFilter.
 * @author Emeric Vernat
 */
public class TestMonitoringFilterInit {
	private static final String FILTER_NAME = "monitoring";
	private FilterConfig config;
	private ServletContext context;
	private MonitoringFilter monitoringFilter;

	/**
	 * Initialisation (deux Before ne garantissent pas l'ordre dans Eclipse).
	 */
	public TestMonitoringFilterInit() {
		super();
		Utils.initialize();
	}

	/**
	 * Initialisation.
	 */
	@Before
	public void setUp() {
		// rq: pas setUpFirst ici car setUp est rappelée dans les méthodes
		try {
			final Field field = MonitoringFilter.class.getDeclaredField("instanceCreated");
			field.setAccessible(true);
			field.set(null, false);
		} catch (final IllegalAccessException e) {
			throw new IllegalStateException(e);
		} catch (final NoSuchFieldException e) {
			throw new IllegalStateException(e);
		}
		config = createNiceMock(FilterConfig.class);
		context = createNiceMock(ServletContext.class);
		expect(config.getServletContext()).andReturn(context).anyTimes();
		expect(config.getFilterName()).andReturn(FILTER_NAME).anyTimes();
		// anyTimes sur getInitParameter car TestJdbcDriver a pu fixer la propriété système à false
		expect(
				context.getInitParameter(Parameters.PARAMETER_SYSTEM_PREFIX
						+ Parameter.DISABLED.getCode())).andReturn(null).anyTimes();
		expect(config.getInitParameter(Parameter.DISABLED.getCode())).andReturn(null).anyTimes();
		expect(context.getMajorVersion()).andReturn(2).anyTimes();
		expect(context.getMinorVersion()).andReturn(5).anyTimes();
		expect(context.getServletContextName()).andReturn("test webapp").anyTimes();
		expect(context.getServerInfo()).andReturn("mockJetty").anyTimes();
		expect(context.getContextPath()).andReturn("/test").anyTimes();
		monitoringFilter = new MonitoringFilter();
	}

	private void destroy() {
		if (monitoringFilter != null) {
			monitoringFilter.destroy();
		}
	}

	/** Test.
	 * @throws ServletException e
	 * @throws IOException e */
	@Test
	public void testInit() throws ServletException, IOException {
		try {
			init();
			setUp();
			expect(config.getInitParameter(Parameter.DISPLAYED_COUNTERS.getCode())).andReturn(
					"http,sql").anyTimes();
			expect(config.getInitParameter(Parameter.HTTP_TRANSFORM_PATTERN.getCode())).andReturn(
					"[0-9]").anyTimes();
			init();
			setUp();
			expect(config.getInitParameter(Parameter.URL_EXCLUDE_PATTERN.getCode())).andReturn(
					"/static/*").anyTimes();
			init();
			setUp();
			expect(config.getInitParameter(Parameter.ALLOWED_ADDR_PATTERN.getCode())).andReturn(
					"127\\.0\\.0\\.1").anyTimes();
			init();

			// pour ce MonitoringFilter, instanceEnabled sera false
			final MonitoringFilter monitoringFilter2 = new MonitoringFilter();
			monitoringFilter2.init(config);
			monitoringFilter2.doFilter(createNiceMock(HttpServletRequest.class),
					createNiceMock(HttpServletResponse.class), createNiceMock(FilterChain.class));
			monitoringFilter2.destroy();
		} finally {
			destroy();
		}
	}

	private void init() throws ServletException {
		replay(config);
		replay(context);
		monitoringFilter.init(config);
		verify(config);
		verify(context);
	}
}
