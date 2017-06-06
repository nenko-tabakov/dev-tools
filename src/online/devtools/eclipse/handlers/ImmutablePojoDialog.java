package online.devtools.eclipse.handlers;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;

class ImmutablePojoDialog extends CheckedTreeSelectionDialog {

	private final SelectedListener finalFieldsSelectedListener = new SelectedListener();
	private final SelectedListener builderSelectedListener = new SelectedListener();

	ImmutablePojoDialog(Shell parent) {
		super(parent, new JavaElementLabelProvider(), new FieldsProvider());
	}

	@Override
	protected CheckboxTreeViewer createTreeViewer(Composite parent) {
		CheckboxTreeViewer treeViewer = super.createTreeViewer(parent);

		addButton(parent, "Generate public final fields instead of getters");
		addButton(parent, "Generate builder instead of public constructor");

		return treeViewer;
	}

	private void addButton(Composite parent, String text) {
		Button selectedButton = new Button(parent, SWT.CHECK);
		selectedButton.setText(text);
		selectedButton.addSelectionListener(new SelectedListener());
	}

	boolean shouldGenerateFinalFields() {
		return finalFieldsSelectedListener.isSelected();
	}

	boolean shouldGenerateBuilder() {
		return builderSelectedListener.isSelected();
	}

	static class SelectedListener implements SelectionListener {

		private boolean selected = false;

		@Override
		public void widgetSelected(SelectionEvent e) {
			selected = Boolean.valueOf((((Button) e.widget).getSelection()));
		}

		@Override
		public void widgetDefaultSelected(SelectionEvent e) {
			widgetSelected(e);
		}

		boolean isSelected() {
			return selected;
		}
	}
}