package online.devtools.eclipse.handlers;

import java.util.Arrays;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.ProjectScope;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.preferences.IScopeContext;
import org.eclipse.core.runtime.preferences.InstanceScope;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaElement;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IOpenable;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.ToolFactory;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.VariableDeclarationExpression;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.jdt.core.formatter.CodeFormatter;
import org.eclipse.jdt.ui.CodeGeneration;
import org.eclipse.jface.text.BadLocationException;
import org.eclipse.jface.text.Document;
import org.eclipse.text.edits.TextEdit;

import online.devtools.eclipse.handlers.tools.CodeSettings;
import online.devtools.eclipse.handlers.tools.GenerationTools;

public class CodeGenerationOperation {

	private final IField[] fields;

	public CodeGenerationOperation(final IField[] fields) {
		this.fields = fields;
	}

	public void run() {
		final ITypeRoot typeRoot = fields[0].getTypeRoot();
		final IType type = typeRoot.findPrimaryType();
		final IJavaProject javaProject = type.getJavaProject();

		ASTParser parser = ASTParser.newParser(AST.JLS8);
		parser.setSource(typeRoot);

		CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		final ASTRewrite astRewrite = ASTRewrite.create(compilationUnit.getAST());

		try {
			ListRewrite listRewriter = getListRewrite(astRewrite, compilationUnit, type);
			final CodeSettings codeSettings = GenerationTools.getCodeGenerationSettings(javaProject);

			for (IField field : fields) {
				final String methodName = GenerationTools.getGetterName(field);
				removeIfExists(methodName, type, listRewriter);

				listRewriter.insertLast(
						createGetterMethod(listRewriter.getASTRewrite().getAST(), field, methodName, codeSettings),
						null);
			}

			save(type.getCompilationUnit(), astRewrite);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private ListRewrite getListRewrite(ASTRewrite astRewrite, CompilationUnit compilationUnit, IType type)
			throws JavaModelException {
		AbstractTypeDeclaration declaration = (AbstractTypeDeclaration) getParent(
				NodeFinder.perform(compilationUnit, type.getNameRange()), AbstractTypeDeclaration.class);
		return astRewrite.getListRewrite(declaration, declaration.getBodyDeclarationsProperty());
	}

	private MethodDeclaration createGetterMethod(AST ast, IField field, String methodName, CodeSettings codeSettings)
			throws JavaModelException {
		final Block body = ast.newBlock();
		body.statements().add(createFieldReturnStatement(ast, field, codeSettings));
		final MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName(methodName));
		methodDeclaration.setReturnType2(createSimpleType(ast, field));
		methodDeclaration.setBody(body);
		methodDeclaration.modifiers().add(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD));

		return methodDeclaration;
	}

	private ReturnStatement createFieldReturnStatement(final AST ast, final IField field,
			final CodeSettings codeSettings) {
		final ReturnStatement returnStatement = ast.newReturnStatement();
		final SimpleName fieldName = createSimpleName(ast, field);
		if (codeSettings.qualifyFieldAccessWithThis) {
			final FieldAccess fieldAccess = ast.newFieldAccess();
			fieldAccess.setExpression(ast.newThisExpression());
			fieldAccess.setName(fieldName);
			returnStatement.setExpression(fieldAccess);
		} else {
			returnStatement.setExpression(fieldName);
		}

		return returnStatement;
	}

	private SimpleName createSimpleName(AST ast, IField field) {
		return ast.newSimpleName(field.getElementName());
	}

	private SimpleType createSimpleType(AST ast, IField field) throws JavaModelException {
		return ast.newSimpleType(ast.newName(Signature.getSignatureSimpleName(field.getTypeSignature())));
	}

	private VariableDeclarationExpression createPublicFinalVariable(AST ast, IField field) throws JavaModelException {
		VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
		variableDeclarationFragment.setName(createSimpleName(ast, field));
		VariableDeclarationExpression variableDeclaration = ast
				.newVariableDeclarationExpression(variableDeclarationFragment);
		variableDeclaration.modifiers().addAll(Arrays.asList(ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD),
				ast.newModifier(ModifierKeyword.FINAL_KEYWORD)));
		variableDeclaration.setType(createSimpleType(ast, field));

		return variableDeclaration;
	}

	private void save(ICompilationUnit cu, ASTRewrite astRewrite) throws CoreException {
		TextEdit edit = astRewrite.rewriteAST();
		cu.applyTextEdit(edit, null);
		cu.save(null, true);
	}

	private ASTNode getParent(ASTNode node, Class<? extends ASTNode> parentClass) {
		do {
			node = node.getParent();
		} while (node != null && !parentClass.isInstance(node));

		return node;
	}

	private String getComments(IField field, CodeSettings codeSettings, String getterName, String lineSeparator)
			throws IllegalArgumentException, JavaModelException, CoreException {
		return CodeGeneration.getGetterComment(field.getCompilationUnit(),
				field.getDeclaringType().getTypeQualifiedName('.'), getterName, field.getElementName(),
				Signature.toString(field.getTypeSignature()), field.getElementName(), lineSeparator);
	}

	private void removeExistingAccessor(final IMethod accessor, final ListRewrite rewrite) throws JavaModelException {
		final MethodDeclaration declaration = (MethodDeclaration) getParent(
				NodeFinder.perform(rewrite.getParent().getRoot(), accessor.getNameRange()), MethodDeclaration.class);
		if (declaration != null)
			rewrite.remove(declaration, null);
	}

	private IMethod findMethod(String name, IType type) throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (isSameMethodSignature(name, new String[0], false, methods[i])) {
				return methods[i];
			}
		}

		return null;
	}

	private boolean isSameMethodSignature(String name, String[] paramTypes, boolean isConstructor, IMethod curr)
			throws JavaModelException {
		if (isConstructor || name.equals(curr.getElementName())) {
			if (isConstructor == curr.isConstructor()) {
				String[] currParamTypes = curr.getParameterTypes();
				if (paramTypes.length == currParamTypes.length) {
					for (int i = 0; i < paramTypes.length; i++) {
						String t1 = Signature.getSimpleName(Signature.toString(paramTypes[i]));
						String t2 = Signature.getSimpleName(Signature.toString(currParamTypes[i]));

						if (!t1.equals(t2)) {
							return false;
						}
					}

					return true;
				}
			}
		}

		return false;
	}

	private void removeIfExists(String methodName, IType type, ListRewrite listRewriter) throws JavaModelException {
		final IMethod existing = findMethod(methodName, type);

		if (existing != null) {
			removeExistingAccessor(existing, listRewriter);
		}
	}

	private String format(IJavaProject project, String source, String lineDelimiter) {
		Map<String, String> options = project != null ? project.getOptions(true) : null;
		TextEdit formattedCode = ToolFactory.createCodeFormatter(options)
				.format(CodeFormatter.K_CLASS_BODY_DECLARATIONS, source, 0, source.length(), 0, lineDelimiter);
		if (formattedCode == null) {
			return source;
		}

		Document document = new Document(source);

		try {
			formattedCode.apply(document, TextEdit.NONE);
		} catch (BadLocationException e) {
			e.printStackTrace();
		}

		return document.get();
	}

	private String getLineDelimiter(IJavaElement elem) {
		IOpenable openable = elem.getOpenable();
		if (openable instanceof ITypeRoot) {
			try {
				return openable.findRecommendedLineSeparator();
			} catch (JavaModelException e) {
				e.printStackTrace();
			}
		}

		IJavaProject project = elem.getJavaProject();
		if (project.exists()) {
			return getProjectLineDelimiter(project);
		}

		return getPlatformLineSeparator(new IScopeContext[] { InstanceScope.INSTANCE });
	}

	private String getProjectLineDelimiter(IJavaProject javaProject) {
		String lineDelimiter = getLineDelimiterPreference(javaProject.getProject());
		if (lineDelimiter != null)
			return lineDelimiter;

		return getSystemLineSeparator();
	}

	private String getLineDelimiterPreference(IProject project) {
		return getPlatformLineSeparator(new IScopeContext[] { new ProjectScope(project) });
	}

	private String getPlatformLineSeparator(IScopeContext[] scopeContext) {
		return Platform.getPreferencesService().getString(Platform.PI_RUNTIME, Platform.PREF_LINE_SEPARATOR,
				getSystemLineSeparator(), scopeContext);
	}

	private String getSystemLineSeparator() {
		return System.getProperty("line.separator", "\n");
	}
}
