/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *     Olivier Prouvost <olivier.prouvost@opcoach.com> 
 *       - Fix bug 428903 : transform this dialog into a part to be defined with spyPart extension
 *       - Fix Bug 428903 - Having a common debug window for all spies 

 *******************************************************************************/
package org.eclipse.e4.tools.event.spy.internal.ui;

import java.util.Collection;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.inject.Inject;

import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.core.di.annotations.Optional;
import org.eclipse.e4.core.services.events.IEventBroker;
import org.eclipse.e4.tools.event.spy.internal.core.EventMonitor;
import org.eclipse.e4.tools.event.spy.internal.model.CapturedEvent;
import org.eclipse.e4.tools.event.spy.internal.model.CapturedEventFilter;
import org.eclipse.e4.tools.event.spy.internal.util.JDTUtils;
import org.eclipse.e4.tools.event.spy.internal.util.LoggerWrapper;
import org.eclipse.e4.ui.model.application.MApplication;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Composite;

public class EventSpyPart implements EventMonitor.NewEventListener {
	private final static String DIALOG_TITLE = "Event spy dialog";

	private final static String[] SHOW_FILTER_LINK_TEXT = new String[]{"Show filters", "Hide filters"};

	private CapturedEventTree capturedEventTree;

	private CapturedEventFilters capturedEventFilters;

	private Composite outer;

	private EventMonitor eventMonitor;

	private ToggleLink showFiltersLink;

	@Inject
	private LoggerWrapper logger;

	@Inject
	private IEventBroker eventBroker;

	@Inject
	private MApplication appplication;
	

	/* Layout scheme:
	 *
	 *  +-- Outer ----------------------------------------+
	 *  | +-- actionBar --------------------------------+ |
	 *  | |                                             | |
	 *  | |  Start capturing events | ShowFiltersLink   | |
	 *  | |                                             | |
	 *  | +---------------------------------------------+ |
	 *  +-------------------------------------------------+
	 *  |                                                 |
	 *  |  CapturedEventFilters                           |
	 *  |                                                 |
	 *  +-------------------------------------------------+
	 *  |                                                 |
	 *  |  CapturedEventTree                              |
	 *  |                                                 |
	 *  +-------------------------------------------------+
	 *  |                                                 |
	 *  |                                          Close  |
	 *  |                                                 |
	 *  +-------------------------------------------------+
	 *
	 * */


	@PostConstruct
	protected void createDialogArea(Composite parent, @Optional SpyPartMemento memento) {
		
		// Bug 428903 : create now a part, and inject directly optional memento (set in saveDialogMemento).
		
		outer = parent;
		
		outer.setLayout(new GridLayout());
		outer.setLayoutData(new GridData(GridData.FILL_BOTH));

		createActionBar(outer);
		createFilters(outer, memento);
		createCapturedEventTree(outer);
	}

	
	@PreDestroy
	private void saveDialogMemento() {
		SpyPartMemento memento = null;
		String baseTopic = capturedEventFilters.getBaseTopic();
		Collection<CapturedEventFilter> filters = capturedEventFilters.getFilters();
		IEclipseContext context = appplication.getContext();
		
		if (!CapturedEventFilters.BASE_EVENT_TOPIC.equals(baseTopic)) {
			memento = new SpyPartMemento();
			memento.setBaseTopic(baseTopic);
		}
		if (!filters.isEmpty()) {
			if (memento == null) {
				memento = new SpyPartMemento();
			}
			memento.setFilters(filters);
		}
		if (memento != null) {
			context.set(SpyPartMemento.class.getName(), memento);
		} else if (context.containsKey(SpyPartMemento.class.getName())) {
			context.remove(SpyPartMemento.class.getName());
		}
	}
	
	private void createActionBar(Composite parent) {
		Composite actionBar = new Composite(parent, SWT.NONE);
		GridData gridData = createDefaultGridData();
		gridData.grabExcessVerticalSpace = false;
		actionBar.setLayoutData(gridData);

		RowLayout rowLayout = new RowLayout(SWT.HORIZONTAL);
		rowLayout.spacing = 20;
		actionBar.setLayout(rowLayout);

		ToggleLink link = new ToggleLink(actionBar);
		link.setText(new String[]{"Start capturing events", "Stop capturing events"});
		link.setClickListener(new ToggleLink.ClickListener() {
			public void clicked(boolean toggled) {
				if (toggled) {
					captureEvents();
				} else {
					stopCaptureEvents();
				}
			}
		});

		showFiltersLink = new ToggleLink(actionBar);
		showFiltersLink.setText(new String[]{SHOW_FILTER_LINK_TEXT[0], SHOW_FILTER_LINK_TEXT[1]});
		showFiltersLink.getControl().setLayoutData(new RowData(130, SWT.DEFAULT));
		showFiltersLink.setClickListener(new ToggleLink.ClickListener() {
			public void clicked(boolean toggled) {
				showFilters(toggled);
			}
		});
	}

	private void createFilters(Composite parent, SpyPartMemento memento) {
		capturedEventFilters = new CapturedEventFilters(outer);
		capturedEventFilters.getControl().setVisible(false);
		GridData gridData = createDefaultGridData();
		gridData.grabExcessVerticalSpace = false;
		gridData.exclude = true;
		capturedEventFilters.getControl().setLayoutData(gridData);
		
		if (memento != null) {
			capturedEventFilters.setBaseTopic(memento.getBaseTopic());
			capturedEventFilters.setFilters(memento.getFilters());
		}
		showFilters(false);
	}

	private void createCapturedEventTree(Composite parent) {
		capturedEventTree = new CapturedEventTree(outer);
		capturedEventTree.getControl().setLayoutData(createDefaultGridData());
		capturedEventTree.setListener(new ICapturedEventTreeListener() {
			public void treeItemWithClassNameClicked(String text) {
				openResource(text);
			}
		});
	}


	public void captureEvents() {
		capturedEventTree.removeAll();
		if (eventMonitor == null) {
			eventMonitor = new EventMonitor(eventBroker);
			eventMonitor.setNewEventListener(this);
		}
		eventMonitor.start(capturedEventFilters.getBaseTopic(), capturedEventFilters.getFilters());
		// getShell().setText(DIALOG_TITLE + " - capturing...");
	}

	public void stopCaptureEvents() {
		if (eventMonitor != null) {
			eventMonitor.stop();
		}
		// getShell().setText(DIALOG_TITLE);
	}

	public void newEvent(CapturedEvent event) {
		capturedEventTree.addEvent(event);
	}

	@SuppressWarnings("restriction")
	private void openResource(String text) {
		try {
			JDTUtils.openClass(text);
		} catch(ClassNotFoundException exc) {
			logger.warn(exc.getMessage());
		}
	}

	private void showFilters(boolean filtersVisible) {
		capturedEventFilters.getControl().setVisible(filtersVisible);
		((GridData) capturedEventFilters.getControl().getLayoutData()).exclude = !filtersVisible;

		//Filters have been set and filters UI is not visible so we have to mark it to user
		if (!filtersVisible && capturedEventFilters.hasFilters()) {
			showFiltersLink.setText(new String[] { String.format("%s (%d)", SHOW_FILTER_LINK_TEXT[0],
				capturedEventFilters.getFiltersCount()), SHOW_FILTER_LINK_TEXT[1]});
		} else {
			showFiltersLink.setText(new String[] {SHOW_FILTER_LINK_TEXT[0], SHOW_FILTER_LINK_TEXT[1]});
		}

		outer.layout(false);
	}	

	private GridData createDefaultGridData() {
		GridData gridData = new GridData();
		gridData.verticalAlignment = GridData.FILL;
		gridData.verticalSpan = 2;
		gridData.grabExcessVerticalSpace = true;
		gridData.horizontalAlignment = GridData.FILL;
		gridData.grabExcessHorizontalSpace = true;
		return gridData;
	}
}
