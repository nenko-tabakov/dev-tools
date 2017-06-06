package online.devtools.eclipse.handlers;

import java.util.Arrays;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

public class GenerateImmutablePojoHandler extends AbstractHandler {

	@Override
	public Object execute(ExecutionEvent event) throws ExecutionException {
		ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(HandlerUtil.getActiveEditor(event).getEditorInput());
		IType primaryType = typeRoot.findPrimaryType();
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		Object[] result = openDialog(window.getShell(), primaryType);

		if (result != null && result.length > 0 && result[0] instanceof IField) {
			new CodeGenerationOperation(Arrays.copyOf(result, result.length, IField[].class)).run();
		}

		return null;
	}

	private Object[] openDialog(Shell shell, IType type) {
		ImmutablePojoDialog dialog = new ImmutablePojoDialog(shell);
		dialog.setTitle("Convert class to immutable POJO");
		dialog.setMessage("Select fields:");
		dialog.setHelpAvailable(false);
		dialog.setInitialSelections(FieldsProvider.getFields(type));
		dialog.setInput(type);
		dialog.setBlockOnOpen(true);

		if (dialog.open() == CheckedTreeSelectionDialog.OK) {
			return dialog.getResult();
		}

		return null;
	}

}
