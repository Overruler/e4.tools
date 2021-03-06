/*******************************************************************************
 * Copyright (c) 2011, 2012 Manumitting Technologies, Inc.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Brian de Alwis (MT) - initial API and implementation
 *     Olivier Prouvost <olivier.prouvost@opcoach.com>
 *       - Fix bug 428903 : transform the 'old' dialog into a part to be defined with spyPart extension
 *******************************************************************************/
package org.eclipse.e4.tools.css.spy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import javax.annotation.PostConstruct;
import javax.inject.Inject;
import javax.inject.Named;

import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.e4.core.contexts.IEclipseContext;
import org.eclipse.e4.ui.css.core.dom.CSSStylableElement;
import org.eclipse.e4.ui.css.core.engine.CSSEngine;
import org.eclipse.e4.ui.css.swt.dom.WidgetElement;
import org.eclipse.e4.ui.css.swt.engine.CSSSWTEngineImpl;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.services.IServiceConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.layout.GridDataFactory;
import org.eclipse.jface.layout.GridLayoutFactory;
import org.eclipse.jface.layout.TableColumnLayout;
import org.eclipse.jface.layout.TreeColumnLayout;
import org.eclipse.jface.viewers.CellEditor;
import org.eclipse.jface.viewers.ColumnLabelProvider;
import org.eclipse.jface.viewers.ColumnViewerEditor;
import org.eclipse.jface.viewers.ColumnViewerEditorActivationStrategy;
import org.eclipse.jface.viewers.ColumnViewerToolTipSupport;
import org.eclipse.jface.viewers.ColumnWeightData;
import org.eclipse.jface.viewers.EditingSupport;
import org.eclipse.jface.viewers.FocusCellOwnerDrawHighlighter;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.TableViewerColumn;
import org.eclipse.jface.viewers.TableViewerEditor;
import org.eclipse.jface.viewers.TableViewerFocusCellManager;
import org.eclipse.jface.viewers.TextCellEditor;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.TreeViewerColumn;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.jface.viewers.ViewerFilter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.DisposeEvent;
import org.eclipse.swt.events.DisposeListener;
import org.eclipse.swt.events.FocusAdapter;
import org.eclipse.swt.events.FocusEvent;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseMoveListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.graphics.Region;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.ToolItem;
import org.eclipse.swt.widgets.Widget;
import org.w3c.css.sac.CSSParseException;
import org.w3c.css.sac.SelectorList;
import org.w3c.dom.NodeList;
import org.w3c.dom.css.CSSStyleDeclaration;
import org.w3c.dom.css.CSSValue;

@SuppressWarnings("restriction")
public class CssSpyPart
{

	@Inject
	@Named(IServiceConstants.ACTIVE_SHELL)
	private Shell activeShell;



	/** @return the CSS element corresponding to the argument, or null if none */
	public static CSSStylableElement getCSSElement(Object o)
	{
		if (o instanceof CSSStylableElement)
		{
			return (CSSStylableElement) o;
		} else
		{
			CSSEngine engine = getCSSEngine(o);
			if (engine != null)
			{
				return (CSSStylableElement) engine.getElement(o);
			}
		}
		return null;
	}

	/** @return the CSS engine governing the argument, or null if none */
	public static CSSEngine getCSSEngine(Object o)
	{
		CSSEngine engine = null;
		if (o instanceof CSSStylableElement)
		{
			CSSStylableElement element = (CSSStylableElement) o;
			engine = WidgetElement.getEngine((Widget) element.getNativeWidget());
		}
		if (engine == null && o instanceof Widget)
		{
			if (((Widget) o).isDisposed())
			{
				return null;
			}
			engine = WidgetElement.getEngine((Widget) o);
		}
		if (engine == null && Display.getCurrent() != null)
		{
			engine = new CSSSWTEngineImpl(Display.getCurrent());
		}
		return engine;
	}

	@Inject
	private Display display;

	private Widget specimen; // specimen (can be reused if reopened)

	@Inject
	IEclipseContext mpartContext; // Used to remember of last specimen.

	private Widget shown;

	private TreeViewer widgetTreeViewer;
	private WidgetTreeProvider widgetTreeProvider;
	private Button showAllShells;
	private TableViewer cssPropertiesViewer;
	private Text cssRules;

	private List<Shell> highlights = new LinkedList<Shell>();
	private List<Region> highlightRegions = new LinkedList<Region>();
	private Text cssSearchBox;
	private Button showUnsetProperties;
	private Button showCssFragment;

	protected ViewerFilter unsetPropertyFilter = new ViewerFilter()
		{

			@Override
			public boolean select(Viewer viewer, Object parentElement, Object element)
			{
				if (element instanceof CSSPropertyProvider)
				{
					try
					{
						return ((CSSPropertyProvider) element).getValue() != null;
					} catch (Exception e)
					{
						return false;
					}
				}
				return false;
			}
		};

	private Composite outer;

	public Widget getSpecimen()
	{
		return specimen;
	}

	private boolean isLive()
	{
		return specimen == null;
	}

	public void setSpecimen(Widget specimen)
	{
		this.specimen = specimen;
		update();
	}

	private Widget getActiveSpecimen()
	{
		if (specimen != null)
		{
			return specimen;
		}
		return display.getCursorControl();
	}

	protected boolean shouldDismissOnLostFocus()
	{
		return false;
	}

	protected void update()
	{
		if (activeShell == null)
		{
			return;
		}
		Widget current = getActiveSpecimen();
		if (shown == current)
		{
			return;
		}
		shown = current;

		CSSEngine engine = getCSSEngine(shown);
		CSSStylableElement element = (CSSStylableElement) engine.getElement(shown);
		if (element == null)
		{
			return;
		}

		updateWidgetTreeInput();
		revealAndSelect(Collections.singletonList(shown));
	}

	private <T> void revealAndSelect(List<T> elements)
	{
		widgetTreeViewer.setSelection(new StructuredSelection(elements), true);
	}

	private void updateForWidgetSelection(ISelection sel)
	{
		disposeHighlights();
		if (sel.isEmpty())
		{
			return;
		}
		StructuredSelection selection = (StructuredSelection) sel;
		for (Object s : selection.toList())
		{
			if (s instanceof Widget)
			{
				highlightWidget((Widget) s);
			}
		}
		populate(selection.size() == 1 && selection.getFirstElement() instanceof Widget ? (Widget) selection.getFirstElement()
				: null);
	}

	private void updateWidgetTreeInput()
	{
		if (showAllShells.getSelection())
		{
			widgetTreeViewer.setInput(display);
		} else
		{
			widgetTreeViewer.setInput(new Object[] { shown instanceof Control ? ((Control) shown).getShell() : shown });
		}
		performCSSSearch(new NullProgressMonitor());
	}

	protected void populate(Widget selected)
	{
		if (selected == null)
		{
			cssPropertiesViewer.setInput(null);
			cssRules.setText("");
			return;
		}
		if (selected.isDisposed())
		{
			cssPropertiesViewer.setInput(null);
			cssRules.setText("*DISPOSED*");
			return;
		}

		CSSStylableElement element = getCSSElement(selected);
		if (element == null)
		{
			cssPropertiesViewer.setInput(null);
			cssRules.setText("Not a stylable element");
			return;
		}

		cssPropertiesViewer.setInput(selected);

		StringBuilder sb = new StringBuilder();
		CSSEngine engine = getCSSEngine(element);
		CSSStyleDeclaration decl = engine.getViewCSS().getComputedStyle(element, null);

		if (element.getCSSStyle() != null)
		{
			sb.append("\nCSS Inline Style(s):\n  ");
			Activator.join(sb, element.getCSSStyle().split(";"), ";\n  ");
		}

		if (decl != null)
		{
			sb.append("\n\nCSS Properties:\n");
			try
			{
				if (decl != null)
				{
					sb.append(decl.getCssText());
				}
			} catch (Exception e)
			{
				sb.append(e);
			}
		}
		if (element.getStaticPseudoInstances().length > 0)
		{
			sb.append("\n\nStatic Pseudoinstances:\n  ");
			Activator.join(sb, element.getStaticPseudoInstances(), "\n  ");
		}

		if (element.getCSSClass() != null)
		{
			sb.append("\n\nCSS Classes:\n  ");
			Activator.join(sb, element.getCSSClass().split(" +"), "\n  ");
		}

		if (element.getAttribute("style") != null)
		{
			sb.append("\n\nSWT Style Bits:\n  ");
			Activator.join(sb, element.getAttribute("style").split(" +"), "\n  ");
		}

		sb.append("\n\nCSS Class Element:\n  ").append(element.getClass().getName());

		// this is useful for diagnosing issues
		if (element.getNativeWidget() instanceof Shell && ((Shell) element.getNativeWidget()).getParent() != null)
		{
			Shell nw = (Shell) element.getNativeWidget();
			sb.append("\n\nShell parent: ").append(nw.getParent());
		}
		if (element.getNativeWidget() instanceof Composite)
		{
			Composite nw = (Composite) element.getNativeWidget();
			sb.append("\n\nSWT Layout: ").append(nw.getLayout());
		}
		Rectangle bounds = getBounds(selected);
		if (bounds != null)
		{
			sb.append("\nBounds: x=").append(bounds.x).append(" y=").append(bounds.y);
			sb.append(" h=").append(bounds.height).append(" w=").append(bounds.width);
		}

		if (element.getNativeWidget() instanceof Widget)
		{
			Widget w = (Widget) element.getNativeWidget();
			if (w.getData() != null)
			{
				sb.append("\nWidget data: ").append(w.getData());
			}
			if (w.getData(SWT.SKIN_ID) != null)
			{
				sb.append("\nWidget Skin ID (").append(SWT.SKIN_ID).append("): ").append(w.getData(SWT.SKIN_ID));
			}
			if (w.getData(SWT.SKIN_CLASS) != null)
			{
				sb.append("\nWidget Skin Class (").append(SWT.SKIN_CLASS).append("): ").append(w.getData(SWT.SKIN_CLASS));
			}
		}

		cssRules.setText(sb.toString().trim());

		disposeHighlights();
		highlightWidget(selected);
	}

	private Shell getShell(Widget widget)
	{
		if (widget instanceof Control)
		{
			return ((Control) widget).getShell();
		}
		return null;
	}

	/** Add a highlight-rectangle for the selected widget */
	private void highlightWidget(Widget selected)
	{
		if (selected == null || selected.isDisposed())
		{
			return;
		}

		Rectangle bounds = getBounds(selected); // relative to absolute display,
												// not the widget
		if (bounds == null /* || bounds.height == 0 || bounds.width == 0 */)
		{
			return;
		}
		// emulate a transparent background as per SWT Snippet180
		Shell selectedShell = getShell(selected);
		// create the highlight; want it to appear on top
		Shell highlight = new Shell(selectedShell, SWT.NO_TRIM | SWT.MODELESS | SWT.NO_FOCUS | SWT.ON_TOP);
		highlight.setBackground(display.getSystemColor(SWT.COLOR_RED));
		Region highlightRegion = new Region();
		highlightRegion.add(0, 0, 1, bounds.height + 2);
		highlightRegion.add(0, 0, bounds.width + 2, 1);
		highlightRegion.add(bounds.width + 1, 0, 1, bounds.height + 2);
		highlightRegion.add(0, bounds.height + 1, bounds.width + 2, 1);
		highlight.setRegion(highlightRegion);
		highlight.setBounds(bounds.x - 1, bounds.y - 1, bounds.width + 2, bounds.height + 2);
		highlight.setEnabled(false);
		highlight.setVisible(true); // not open(): setVisible() prevents taking
									// focus

		highlights.add(highlight);
		highlightRegions.add(highlightRegion);
	}

	private void disposeHighlights()
	{
		for (Shell highlight : highlights)
		{
			highlight.dispose();
		}
		highlights.clear();
		for (Region region : highlightRegions)
		{
			region.dispose();
		}
		highlightRegions.clear();
	}

	private Rectangle getBounds(Widget widget)
	{
		if (widget instanceof Shell)
		{
			// Shell bounds are already in display coordinates
			return ((Shell) widget).getBounds();
		} else if (widget instanceof Control)
		{
			Control control = (Control) widget;
			Rectangle bounds = control.getBounds();
			return control.getDisplay().map(control.getParent(), null, bounds);
		} else if (widget instanceof ToolItem)
		{
			ToolItem item = (ToolItem) widget;
			Rectangle bounds = item.getBounds();
			return item.getDisplay().map(item.getParent(), null, bounds);
		} else if (widget instanceof CTabItem)
		{
			CTabItem item = (CTabItem) widget;
			Rectangle bounds = item.getBounds();
			return item.getDisplay().map(item.getParent(), null, bounds);
		}
		// FIXME: figure out how to map items to a position
		return null;
	}

	/**
	 * Create contents of the spy.
	 *
	 * @param parent
	 */
	@PostConstruct
	protected Control createDialogArea(Composite parent, IEclipseContext ctx)
	{
		outer = parent;
		outer.setLayout(new GridLayout());
		outer.setLayoutData(new GridData(GridData.FILL_BOTH));

		Composite top = new Composite(outer, SWT.NONE);
		GridLayoutFactory.swtDefaults().numColumns(2).applyTo(top);
		cssSearchBox = new Text(top, SWT.BORDER | SWT.SEARCH | SWT.ICON_SEARCH | SWT.ICON_CANCEL);
		cssSearchBox.setMessage("CSS Selector");
		cssSearchBox.setToolTipText("Highlight matching widgets");
		GridDataFactory.fillDefaults().grab(true, false).applyTo(cssSearchBox);

		showAllShells = new Button(top, SWT.CHECK);
		showAllShells.setText("All shells");
		GridDataFactory.swtDefaults().applyTo(showAllShells);
		GridDataFactory.fillDefaults().applyTo(top);

		SashForm sashForm = new SashForm(outer, SWT.VERTICAL);
		sashForm.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		// / THE WIDGET TREE
		Composite widgetsComposite = new Composite(sashForm, SWT.NONE);

		widgetTreeViewer = new TreeViewer(widgetsComposite, SWT.BORDER | SWT.MULTI);
		widgetTreeProvider = new WidgetTreeProvider();
		widgetTreeViewer.setContentProvider(widgetTreeProvider);
		widgetTreeViewer.setAutoExpandLevel(0);
		widgetTreeViewer.getTree().setLinesVisible(true);
		widgetTreeViewer.getTree().setHeaderVisible(true);
		ColumnViewerToolTipSupport.enableFor(widgetTreeViewer);

		TreeViewerColumn widgetTypeColumn = new TreeViewerColumn(widgetTreeViewer, SWT.NONE);
		widgetTypeColumn.getColumn().setWidth(100);
		widgetTypeColumn.getColumn().setText("Widget");
		widgetTypeColumn.setLabelProvider(new ColumnLabelProvider()
			{
				@Override
				public String getText(Object item)
				{
					CSSStylableElement element = CssSpyPart.getCSSElement(item);
					return element.getLocalName() + " (" + element.getNamespaceURI() + ")";
				}
			});

		TreeViewerColumn widgetClassColumn = new TreeViewerColumn(widgetTreeViewer, SWT.NONE);
		widgetClassColumn.getColumn().setText("CSS Class");
		widgetClassColumn.getColumn().setWidth(100);
		widgetClassColumn.setLabelProvider(new ColumnLabelProvider()
			{
				@Override
				public String getText(Object item)
				{
					CSSStylableElement element = CssSpyPart.getCSSElement(item);
					if (element.getCSSClass() == null)
					{
						return null;
					}
					String classes[] = element.getCSSClass().split(" +");
					return classes.length <= 1 ? classes[0] : classes[0] + " (+" + (classes.length - 1) + " others)";
				}

				@Override
				public String getToolTipText(Object item)
				{
					CSSStylableElement element = CssSpyPart.getCSSElement(item);
					if (element == null)
					{
						return null;
					}
					StringBuilder sb = new StringBuilder();
					sb.append(element.getLocalName()).append(" (").append(element.getNamespaceURI()).append(")");
					if (element.getCSSClass() != null)
					{
						sb.append("\nClasses:\n  ");
						Activator.join(sb, element.getCSSClass().split(" +"), "\n  ");
					}
					return sb.toString();
				}
			});

		TreeViewerColumn widgetIdColumn = new TreeViewerColumn(widgetTreeViewer, SWT.NONE);
		widgetIdColumn.getColumn().setWidth(100);
		widgetIdColumn.getColumn().setText("CSS Id");
		widgetIdColumn.setLabelProvider(new ColumnLabelProvider()
			{
				@Override
				public String getText(Object item)
				{
					CSSStylableElement element = CssSpyPart.getCSSElement(item);
					return element.getCSSId();
				}
			});

		TreeColumnLayout widgetsTableLayout = new TreeColumnLayout();
		widgetsTableLayout.setColumnData(widgetTypeColumn.getColumn(), new ColumnWeightData(50));
		widgetsTableLayout.setColumnData(widgetIdColumn.getColumn(), new ColumnWeightData(40));
		widgetsTableLayout.setColumnData(widgetClassColumn.getColumn(), new ColumnWeightData(40));
		widgetsComposite.setLayout(widgetsTableLayout);

		// / HEADERS
		Composite container = new Composite(sashForm, SWT.NONE);
		container.setLayout(new GridLayout(2, true));

		Label lblCssProperties = new Label(container, SWT.NONE);
		lblCssProperties.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblCssProperties.setText("CSS Properties");

		Label lblCssRules = new Label(container, SWT.NONE);
		lblCssRules.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false, 1, 1));
		lblCssRules.setText("CSS Rules");

		// // THE CSS PROPERTIES TABLE
		Composite propsComposite = new Composite(container, SWT.BORDER);
		GridData gridData = new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1);
		gridData.minimumHeight = 50;
		propsComposite.setLayoutData(gridData);

		cssPropertiesViewer = new TableViewer(propsComposite, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION);
		cssPropertiesViewer.setContentProvider(new CSSPropertiesContentProvider());
		cssPropertiesViewer.getTable().setLinesVisible(true);
		cssPropertiesViewer.getTable().setHeaderVisible(true);
		cssPropertiesViewer.setComparator(new ViewerComparator());

		final TextCellEditor textCellEditor = new TextCellEditor(cssPropertiesViewer.getTable());
		TableViewerEditor.create(cssPropertiesViewer, new TableViewerFocusCellManager(cssPropertiesViewer,
				new FocusCellOwnerDrawHighlighter(cssPropertiesViewer)), new ColumnViewerEditorActivationStrategy(
				cssPropertiesViewer), ColumnViewerEditor.TABBING_HORIZONTAL | ColumnViewerEditor.TABBING_MOVE_TO_ROW_NEIGHBOR
				| ColumnViewerEditor.TABBING_VERTICAL | ColumnViewerEditor.KEYBOARD_ACTIVATION);

		TableViewerColumn propName = new TableViewerColumn(cssPropertiesViewer, SWT.NONE);
		propName.getColumn().setWidth(100);
		propName.getColumn().setText("Property");
		propName.setLabelProvider(new ColumnLabelProvider()
			{
				@Override
				public String getText(Object element)
				{
					return ((CSSPropertyProvider) element).getPropertyName();
				}
			});

		TableViewerColumn propValue = new TableViewerColumn(cssPropertiesViewer, SWT.NONE);
		propValue.getColumn().setWidth(100);
		propValue.getColumn().setText("Value");
		propValue.setLabelProvider(new ColumnLabelProvider()
			{
				@Override
				public String getText(Object element)
				{
					try
					{
						return ((CSSPropertyProvider) element).getValue();
					} catch (Exception e)
					{
						System.err.println("Error fetching property: " + element + ": " + e);
						return null;
					}
				}
			});
		propValue.setEditingSupport(new EditingSupport(cssPropertiesViewer)
			{
				@Override
				protected CellEditor getCellEditor(Object element)
				{
					// do the fancy footwork here to return an appropriate
					// editor to
					// the value-type
					return textCellEditor;
				}

				@Override
				protected boolean canEdit(Object element)
				{
					return true;
				}

				@Override
				protected Object getValue(Object element)
				{
					try
					{
						String value = ((CSSPropertyProvider) element).getValue();
						return value == null ? "" : value;
					} catch (Exception e)
					{
						return "";
					}
				}

				@Override
				protected void setValue(Object element, Object value)
				{
					try
					{
						if (value == null || ((String) value).trim().length() == 0)
						{
							return;
						}
						CSSPropertyProvider provider = (CSSPropertyProvider) element;
						provider.setValue((String) value);
					} catch (Exception e)
					{
						MessageDialog.openError(activeShell, "Error", "Unable to set property:\n\n" + e.getMessage());
					}
					cssPropertiesViewer.update(element, null);
				}
			});

		TableColumnLayout propsTableLayout = new TableColumnLayout();
		propsTableLayout.setColumnData(propName.getColumn(), new ColumnWeightData(50));
		propsTableLayout.setColumnData(propValue.getColumn(), new ColumnWeightData(50));
		propsComposite.setLayout(propsTableLayout);

		// / THE CSS RULES
		cssRules = new Text(container, SWT.BORDER | SWT.H_SCROLL | SWT.V_SCROLL | SWT.MULTI);
		cssRules.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 1, 1));

		// / THE CSS PROPERTIES TABLE (again)
		showUnsetProperties = new Button(container, SWT.CHECK);
		showUnsetProperties.setText("Show unset properties");
		showCssFragment = new Button(container, SWT.PUSH);
		showCssFragment.setText("Show CSS fragment");
		showCssFragment.setToolTipText("Generates CSS rule block for the selected widget");

		// and for balance
		new Label(container, SWT.NONE);

		// / The listeners

		cssSearchBox.addModifyListener(new ModifyListener()
			{
				private Runnable updater;
				private IProgressMonitor monitor;

				@Override
				public void modifyText(ModifyEvent e)
				{
					if (monitor != null)
					{
						monitor.setCanceled(false);
					}
					display.timerExec(200, updater = new Runnable()
						{
							@Override
							public void run()
							{
								if (updater == this)
								{
									performCSSSearch(monitor = new NullProgressMonitor());
								}
							}
						});
				}
			});
		cssSearchBox.addKeyListener(new KeyAdapter()
			{
				@Override
				public void keyPressed(KeyEvent e)
				{
					if (e.keyCode == SWT.ARROW_DOWN && (e.stateMask & SWT.MODIFIER_MASK) == 0)
					{
						widgetTreeViewer.getControl().setFocus();
					}
				}
			});

		widgetTreeViewer.addSelectionChangedListener(new ISelectionChangedListener()
			{
				@Override
				public void selectionChanged(SelectionChangedEvent event)
				{
					updateForWidgetSelection(event.getSelection());
					showCssFragment.setEnabled(!event.getSelection().isEmpty());
				}
			});
		if (isLive())
		{
			container.addMouseMoveListener(new MouseMoveListener()
				{
					@Override
					public void mouseMove(MouseEvent e)
					{
						update();
					}
				});
		}

		if (shouldDismissOnLostFocus())
		{
			container.addFocusListener(new FocusAdapter()
				{
					@Override
					public void focusLost(FocusEvent e)
					{
						// setReturnCode(Window.OK);
						// close();
					}
				});
		}
		/*
		 * container.addKeyListener(new KeyAdapter() {
		 *
		 * @Override public void keyPressed(KeyEvent e) { if (e.character ==
		 * SWT.ESC) { cancelPressed(); } else if (e.character == SWT.CR |
		 * e.character == SWT.LF) { okPressed(); } } });
		 */
		showAllShells.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					updateWidgetTreeInput();
				}
			});

		outer.addDisposeListener(new DisposeListener()
			{
				@Override
				public void widgetDisposed(DisposeEvent e)
				{
					dispose();
				}
			});

		showUnsetProperties.setSelection(true);
		showUnsetProperties.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					if (showUnsetProperties.getSelection())
					{
						cssPropertiesViewer.removeFilter(unsetPropertyFilter);
					} else
					{
						cssPropertiesViewer.addFilter(unsetPropertyFilter);
					}
				}
			});

		showCssFragment.addSelectionListener(new SelectionAdapter()
			{
				@Override
				public void widgetSelected(SelectionEvent e)
				{
					showCssFragment();
				}

				@Override
				public void widgetDefaultSelected(SelectionEvent e)
				{
					widgetSelected(e);
				}
			});

		/*
		 * if ((specimen != null) && !specimen.isDisposed()) { // Reopen this
		 * part from a toolbar and widget always displayed. update(); } else {
		 * if ((specimen != null) && !specimen.isDisposed()) { // Reopen this
		 * part from a toolbar and widget always displayed. update(); } else {
		 * // Must set a specimen from application. Control control =
		 * display.getCursorControl(); // it may be that only the shell was
		 * selected if (control == null) { control = display.getActiveShell();
		 * if (control.getParent() != null) { // Take the main shell of this
		 * window (spy window) control = control.getParent(); } }
		 * setSpecimen(control); } }
		 */

		// update(); (called twice)
		sashForm.setWeights(new int[] { 50, 50 });
		widgetTreeViewer.getControl().setFocus();

		return outer;
	}

	/** This method listen to current part and adapt the contents of spy part. */
	@Inject
	protected void reactOnActivate(@Named(IServiceConstants.ACTIVE_PART) MPart p, MPart cssPart,
			@Named(IServiceConstants.ACTIVE_SHELL) Shell s)
	{
		if (outer == null)
		{
			// Do nothing if no UI created.
			return;
		}
		activeShell = s;

		// Check if control is in the css spy part shell.
		Control control = display.getCursorControl();

		Shell controlShell = (control == null) ? display.getActiveShell() : control.getShell();
		Shell spyPartShell = outer.getShell();

		if (p != cssPart)
		{
			// Must remove the highlights if selected
			disposeHighlights();

		} else if (spyPartShell != controlShell)
		{
			// A widget has been selected in another shell.. We can display the
			// corresponding control as a specimen
			shown = null;
			setSpecimen(control);
		}


	}

	protected void showCssFragment()
	{
		if (!(widgetTreeViewer.getSelection() instanceof IStructuredSelection) || widgetTreeViewer.getSelection().isEmpty())
		{
			return;
		}

		StringBuilder sb = new StringBuilder();
		for (Object o : ((IStructuredSelection) widgetTreeViewer.getSelection()).toArray())
		{
			if (o instanceof Widget)
			{
				if (sb.length() > 0)
				{
					sb.append('\n');
				}
				addCssFragment((Widget) o, sb);
			}
		}
		TextPopupDialog tpd = new TextPopupDialog(widgetTreeViewer.getControl().getShell(), "CSS", sb.toString(), true,
				"Escape to dismiss");
		tpd.open();
	}

	private void addCssFragment(Widget w, StringBuilder sb)
	{
		CSSStylableElement element = getCSSElement(w);
		if (element == null)
		{
			return;
		}

		sb.append(element.getLocalName());
		if (element.getCSSId() != null)
		{
			sb.append("#").append(element.getCSSId());
		}
		sb.append(" {");

		CSSEngine engine = getCSSEngine(element);
		// we first check the viewCSS and then the property values
		CSSStyleDeclaration decl = engine.getViewCSS().getComputedStyle(element, null);

		List<String> propertyNames = new ArrayList<String>(engine.getCSSProperties(element));
		Collections.sort(propertyNames);

		int count = 0;

		// First list the generated properties
		for (Iterator<String> iter = propertyNames.iterator(); iter.hasNext();)
		{
			String propertyName = iter.next();
			String genValue = trim(engine.retrieveCSSProperty(element, propertyName, ""));
			String declValue = null;

			if (genValue == null)
			{
				continue;
			}

			if (decl != null)
			{
				CSSValue cssValue = decl.getPropertyCSSValue(propertyName);
				if (cssValue != null)
				{
					declValue = trim(cssValue.getCssText());
				}
			}
			if (count == 0)
			{
				sb.append("\n  /* actual values */");
			}
			sb.append("\n  ").append(propertyName).append(": ").append(genValue).append(";");
			if (declValue != null)
			{
				sb.append("\t/* declared in CSS: ").append(declValue).append(" */");
			}
			count++;
			iter.remove(); // remove so we don't re-report below
		}

		// then list any declared properties; generated properties already
		// removed
		if (decl != null)
		{
			int declCount = 0;
			for (String propertyName : propertyNames)
			{
				String declValue = null;
				CSSValue cssValue = decl.getPropertyCSSValue(propertyName);
				if (cssValue != null)
				{
					declValue = trim(cssValue.getCssText());
				}
				if (declValue == null)
				{
					continue;
				}
				if (declCount == 0)
				{
					sb.append("\n\n  /* declared in CSS rules */");
				}
				sb.append("\n  ").append(propertyName).append(": ").append(declValue).append(";");
				count++;
				declCount++;
			}
		}
		sb.append(count > 0 ? "\n}" : "}");
	}

	/** Trim the string; return null if empty */
	private String trim(String s)
	{
		if (s == null)
		{
			return null;
		}
		s = s.trim();
		return s.length() > 0 ? s : null;
	}

	protected void performCSSSearch(IProgressMonitor progress)
	{
		List<Widget> widgets = new ArrayList<Widget>();
		performCSSSearch(progress, cssSearchBox.getText(), widgets);
		if (!progress.isCanceled())
		{
			revealAndSelect(widgets);
		}
	}

	private void performCSSSearch(IProgressMonitor monitor, String text, Collection<Widget> results)
	{
		if (text.trim().length() == 0)
		{
			return;
		}
		widgetTreeViewer.collapseAll();
		Object[] roots = widgetTreeProvider.getElements(widgetTreeViewer.getInput());
		monitor.beginTask("Searching for \"" + text + "\"", roots.length * 10);
		for (Object root : roots)
		{
			if (monitor.isCanceled())
			{
				return;
			}

			CSSStylableElement element = getCSSElement(root);
			if (element == null)
			{
				continue;
			}

			CSSEngine engine = getCSSEngine(root);
			try
			{
				SelectorList selectors = engine.parseSelectors(text);
				monitor.worked(2);
				processCSSSearch(new SubProgressMonitor(monitor, 8), engine, selectors, element, null, results);
			} catch (CSSParseException e)
			{
				System.out.println(e.toString());
			} catch (IOException e)
			{
				System.out.println(e.toString());
			}
		}
		monitor.done();
	}

	private void processCSSSearch(IProgressMonitor monitor, CSSEngine engine, SelectorList selectors, CSSStylableElement element,
			String pseudo, Collection<Widget> results)
	{
		if (monitor.isCanceled())
		{
			return;
		}
		NodeList children = element.getChildNodes();
		monitor.beginTask("Searching", 5 + 5 * children.getLength());
		boolean matched = false;
		for (int i = 0; i < selectors.getLength(); i++)
		{
			if (matched = engine.matches(selectors.item(i), element, pseudo))
			{
				break;
			}
		}
		if (matched)
		{
			results.add((Widget) element.getNativeWidget());
		}
		monitor.worked(5);
		for (int i = 0; i < children.getLength(); i++)
		{
			if (monitor.isCanceled())
			{
				return;
			}
			processCSSSearch(new SubProgressMonitor(monitor, 5), engine, selectors, (CSSStylableElement) children.item(i), pseudo,
					results);
		}
		monitor.done();
	}

	protected void dispose()
	{
		disposeHighlights();
	}

}
