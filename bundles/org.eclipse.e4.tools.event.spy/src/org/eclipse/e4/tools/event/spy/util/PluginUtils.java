/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.e4.tools.event.spy.util;

import org.eclipse.e4.tools.event.spy.Constants;
import org.osgi.framework.Bundle;
import org.osgi.framework.FrameworkUtil;

public class PluginUtils {
	public static String getContributorURI() {
		return String.format("platform:/plugin/%s", Constants.PLUGIN_ID);
	}

	public static String getContributionURI(Class<?> contributionCls) {
		return String.format("bundleclass://%s/%s", Constants.PLUGIN_ID, contributionCls.getName());
	}

	public static String getBundleId(Class<?> cls) {
		Bundle bundle = FrameworkUtil.getBundle(cls);
		if (bundle == null) {
			throw new IllegalArgumentException("Cannot find bundle for class: " + cls.getName());
		}
		return bundle.getSymbolicName();
	}
}
