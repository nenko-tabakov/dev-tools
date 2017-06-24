package online.devtools.eclipse.handlers;

import org.eclipse.jdt.ui.JavaElementLabelProvider;
import org.eclipse.jface.viewers.CheckboxTreeViewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;

class ImmutablePojoDialog extends CheckedTreeSelectionDialog {

	private final SelectedListener finalFieldsSelectedListener = new SelectedListener();
	private final SelectedListener builderSelectedListener = new SelectedListener();
	private final SelectedListener replaceExistingDeclarations = new SelectedListener();
	private final SelectedListener makeParametersFinal = new SelectedListener();
	private final SelectedListener addComments = new SelectedListener();

	ImmutablePojoDialog(Shell parent) {
		super(parent, new JavaElementLabelProvider(), new FieldsProvider());
	}

	@Override

	protected CheckboxTreeViewer createTreeViewer(Composite parent) {
		CheckboxTreeViewer treeViewer = super.createTreeViewer(parent);
		// TODO: Add link to the preference page for the comments contents

		addButton(parent, "Generate method comments", addComments);
		addSeparator(parent);
		addButton(parent, "Do not replace existing declarations", replaceExistingDeclarations);
		addButton(parent, "Add final modifier for parameters", makeParametersFinal);
		addSeparator(parent);
		addButton(parent, "Generate public final fields instead of getters", finalFieldsSelectedListener);
		addButton(parent, "Generate builder instead of public constructor", builderSelectedListener);

		// TODO: Add message what will be changed/removed from the existing code
		return treeViewer;
	}

	private void addButton(Composite parent, String text, SelectedListener listener) {
		Button selectedButton = new Button(parent, SWT.CHECK);
		selectedButton.setText(text);
		selectedButton.addSelectionListener(listener);
	}

	private void addSeparator(Composite parent) {
		Label separator = new Label(parent, SWT.HORIZONTAL | SWT.SEPARATOR);
		separator.setLayoutData(new GridData(GridData.FILL_HORIZONTAL));
	}

	boolean shouldGenerateFinalFields() {
		return finalFieldsSelectedListener.isSelected();
	}

	boolean shouldGenerateBuilder() {
		return builderSelectedListener.isSelected();
	}

	boolean shouldReplaceExistingDeclarations() {
		return !replaceExistingDeclarations.isSelected();
	}

	boolean shouldMakeParametersFinal() {
		return makeParametersFinal.isSelected();
	}

	boolean shouldAddComments() {
		return addComments.isSelected();
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