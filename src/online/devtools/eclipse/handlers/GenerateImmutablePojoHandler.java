package online.devtools.eclipse.handlers;

import java.util.Arrays;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.dialogs.CheckedTreeSelectionDialog;
import org.eclipse.ui.handlers.HandlerUtil;

import online.devtools.eclipse.handlers.tools.CodeSettings;
import online.devtools.eclipse.handlers.tools.GenerationTools;

public class GenerateImmutablePojoHandler extends AbstractHandler {

	@Override

	public Object execute(ExecutionEvent event) throws ExecutionException {
		ITypeRoot typeRoot = JavaUI.getEditorInputTypeRoot(HandlerUtil.getActiveEditor(event).getEditorInput());
		IType primaryType = typeRoot.findPrimaryType();
		IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);
		ImmutablePojoDialog dialog = openDialog(window.getShell(), primaryType);

		if (dialog != null) {
			Object[] result = dialog.getResult();
			if (result != null && result.length > 0 && result[0] instanceof IField) {
				new CodeGenerationOperation(Arrays.copyOf(result, result.length, IField[].class),
						createCodeGenerationSettings(dialog, primaryType.getJavaProject())).run();
			}
		}
		return null;
	}

	private CodeSettings createCodeGenerationSettings(ImmutablePojoDialog dialog, IJavaProject javaProject) {
		return CodeSettings.builder().withCodeSettings(GenerationTools.getCodeGenerationSettings(javaProject))
				.withReplaceExistingDeclarations(dialog.shouldReplaceExistingDeclarations())
				.withMakeParametersFinal(dialog.shouldMakeParametersFinal())
				.withGenerateBuilder(dialog.shouldGenerateBuilder())
				.withGenerateFinalFields(dialog.shouldGenerateFinalFields()).withAddComments(dialog.shouldAddComments())
				.build();
	}

	private ImmutablePojoDialog openDialog(Shell shell, IType type) {
		ImmutablePojoDialog dialog = new ImmutablePojoDialog(shell);
		dialog.setTitle("Make Immutable");
		dialog.setMessage("Select fields:");
		dialog.setHelpAvailable(false);
		dialog.setInitialSelections(FieldsProvider.getFields(type));
		dialog.setInput(type);
		dialog.setBlockOnOpen(true);

		if (dialog.open() == CheckedTreeSelectionDialog.OK) {
			return dialog;
		}

		return null;
	}
}