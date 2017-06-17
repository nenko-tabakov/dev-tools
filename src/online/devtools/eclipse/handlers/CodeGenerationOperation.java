package online.devtools.eclipse.handlers;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.ISourceRange;
import org.eclipse.jdt.core.ISourceReference;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.ITypeRoot;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.core.dom.AST;
import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTParser;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.Assignment;
import org.eclipse.jdt.core.dom.Block;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldAccess;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.IExtendedModifier;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.Modifier;
import org.eclipse.jdt.core.dom.Modifier.ModifierKeyword;
import org.eclipse.jdt.core.dom.NodeFinder;
import org.eclipse.jdt.core.dom.ReturnStatement;
import org.eclipse.jdt.core.dom.SimpleName;
import org.eclipse.jdt.core.dom.SimpleType;
import org.eclipse.jdt.core.dom.SingleVariableDeclaration;
import org.eclipse.jdt.core.dom.VariableDeclarationFragment;
import org.eclipse.jdt.core.dom.rewrite.ASTRewrite;
import org.eclipse.jdt.core.dom.rewrite.ListRewrite;
import org.eclipse.text.edits.TextEdit;

import online.devtools.eclipse.handlers.tools.CodeSettings;
import online.devtools.eclipse.handlers.tools.GenerationTools;

public class CodeGenerationOperation {

	private final IField[] fields;

	private final CodeSettings codeGenerationSettings;

	public CodeGenerationOperation(final IField[] fields, final CodeSettings codeGenerationSettings) {
		this.fields = fields;
		this.codeGenerationSettings = codeGenerationSettings;
	}

	public void run() {
		final ITypeRoot typeRoot = fields[0].getTypeRoot();
		final IType type = typeRoot.findPrimaryType();
		final ASTParser parser = ASTParser.newParser(AST.JLS8);

		parser.setSource(typeRoot);

		final CompilationUnit compilationUnit = (CompilationUnit) parser.createAST(null);
		final ASTRewrite astRewrite = ASTRewrite.create(compilationUnit.getAST());
		final AST ast = astRewrite.getAST();

		try {
			final AbstractTypeDeclaration parent = getParent(compilationUnit, type, AbstractTypeDeclaration.class);
			final ListRewrite listRewrite = astRewrite.getListRewrite(parent, parent.getBodyDeclarationsProperty());
			ASTNode insertionPoint = null;

			if (codeGenerationSettings.generateBuilder) {
				// TODO: Generate:
				// -- private constructor
				// -- inner static Builder class
				// -- public static builder() method that instantiate the
				// builder
				// -- remove all previously declared constructors
				insertionPoint = addConstructor(listRewrite, astRewrite, type, ast.newModifiers(Modifier.PRIVATE));
			} else {
				insertionPoint = addConstructor(listRewrite, astRewrite, type, ast.newModifiers(Modifier.PUBLIC));
			}

			// TODO: For both cases remove setters if exist
			if (codeGenerationSettings.generateFinalFields) {
				// TODO: Do not delete and create new fields but change the
				// modifiers of the existing ones
				addPublicFinalFields(listRewrite, ast);
			} else {
				addGetters(listRewrite, astRewrite, compilationUnit, type, insertionPoint);
			}

			save(type.getCompilationUnit(), astRewrite);
		} catch (CoreException e) {
			e.printStackTrace();
		}
	}

	private MethodDeclaration addConstructor(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final IType type, final Collection<Modifier> modifiers) throws JavaModelException {
		final AST ast = astRewrite.getAST();
		final String constructorName = getType(fields[0]);
		final MethodDeclaration constructor = getConstructor(constructorName, fields, astRewrite, modifiers);

		listRewrite.insertAfter(constructor, getLastField(listRewrite), null);

		return constructor;
	}

	private void addGetters(final ListRewrite listRewrite, final ASTRewrite astRewrite,
			final CompilationUnit compilationUnit, final IType type, final ASTNode insertPoint)
			throws JavaModelException {
		final AST ast = astRewrite.getAST();
		for (IField field : fields) {
			final String methodName = GenerationTools.getGetterName(field);
			final IMethod existingMethod = findMethod(methodName, type, false);
			if (existingMethod == null
					|| (existingMethod != null && codeGenerationSettings.replaceExistingDeclarations)) {
				removeAccessor(existingMethod, listRewrite);
				listRewrite.insertAfter(createGetterMethod(ast, field, methodName), insertPoint, null);
				addFinalFieldModifier(astRewrite, compilationUnit, field);
			}
		}
	}

	private void addPublicFinalFields(final ListRewrite listRewrite, final AST ast) throws JavaModelException {
		for (IField field : fields) {
			listRewrite.insertFirst(createPublicFinalVariable(ast, field), null);
			field.delete(false, null);
		}
	}

	private ASTNode getLastField(final ListRewrite listRewrite) throws JavaModelException {
		return getNode(listRewrite, fields[fields.length - 1]);
	}

	private void save(ICompilationUnit cu, ASTRewrite astRewrite) throws CoreException {
		TextEdit edit = astRewrite.rewriteAST();
		cu.applyTextEdit(edit, null);
		cu.save(null, true);
	}

	private ASTNode getNode(final ListRewrite listRewrite, final IField field) throws JavaModelException {
		ISourceRange sourceRange = field.getSourceRange();
		if (sourceRange == null) {
			return null;
		}

		int insertPos = sourceRange.getOffset();
		final List<? extends ASTNode> members = listRewrite.getOriginalList();
		for (ASTNode node : members) {
			if (node.getStartPosition() >= insertPos) {
				return node;
			}
		}

		return null;
	}

	private ASTNode getParent(ASTNode node, Class<? extends ASTNode> parentClass) {
		do {
			node = node.getParent();
		} while (node != null && !parentClass.isInstance(node));
		return node;
	}

	private <T extends ASTNode> T getParent(CompilationUnit compilationUnit, ISourceReference sourceReference,
			Class<? extends ASTNode> nodeType) throws JavaModelException {
		return (T) getParent(NodeFinder.perform(compilationUnit, sourceReference.getNameRange()), nodeType);
	}

	private void addFinalFieldModifier(final ASTRewrite astRewrite, final CompilationUnit compilationUnit,
			final IField field) throws JavaModelException {
		final FieldDeclaration fieldDeclaration = getParent(compilationUnit, field, FieldDeclaration.class);
		if (fieldDeclaration != null && !isFinal(fieldDeclaration)) {
			final AST ast = astRewrite.getAST();
			final ListRewrite fieldRewrite = astRewrite.getListRewrite(fieldDeclaration,
					FieldDeclaration.MODIFIERS2_PROPERTY);
			fieldRewrite.insertLast(createFinalModifier(ast), null);
		}
	}

	private boolean isFinal(final FieldDeclaration fieldDeclaration) {
		final List<IExtendedModifier> modifiers = fieldDeclaration.modifiers();
		if (modifiers != null) {
			for (IExtendedModifier modifier : modifiers) {
				if (modifier.isModifier() && modifier instanceof Modifier) {
					if (((Modifier) modifier).isFinal()) {
						return true;
					}
				}
			}
		}
		return false;
	}

	private MethodDeclaration createGetterMethod(final AST ast, final IField field, final String methodName)
			throws JavaModelException {
		final Block body = ast.newBlock();
		body.statements().add(createFieldReturnStatement(ast, field));
		final MethodDeclaration methodDeclaration = ast.newMethodDeclaration();
		methodDeclaration.setName(ast.newSimpleName(methodName));
		methodDeclaration.setReturnType2(createSimpleType(ast, field));
		methodDeclaration.setBody(body);
		methodDeclaration.modifiers().add(createPublicModifier(ast));
		return methodDeclaration;
	}

	private ReturnStatement createFieldReturnStatement(final AST ast, final IField field) {
		final ReturnStatement returnStatement = ast.newReturnStatement();
		final SimpleName fieldName = createSimpleName(ast, field);
		if (codeGenerationSettings.qualifyFieldAccessWithThis) {
			final FieldAccess fieldAccess = ast.newFieldAccess();
			fieldAccess.setExpression(ast.newThisExpression());
			fieldAccess.setName(fieldName);
			returnStatement.setExpression(fieldAccess);
		} else {
			returnStatement.setExpression(fieldName);
		}
		return returnStatement;
	}

	private SimpleName createSimpleName(final AST ast, final IField field) {
		return ast.newSimpleName(field.getElementName());
	}

	private SimpleType createSimpleType(final AST ast, final IField field) throws JavaModelException {
		return ast.newSimpleType(ast.newName(Signature.getSignatureSimpleName(field.getTypeSignature())));
	}

	private FieldDeclaration createPublicFinalVariable(final AST ast, final IField field) throws JavaModelException {
		final VariableDeclarationFragment variableDeclarationFragment = ast.newVariableDeclarationFragment();
		variableDeclarationFragment.setName(createSimpleName(ast, field));
		final FieldDeclaration fieldDeclaration = ast.newFieldDeclaration(variableDeclarationFragment);
		fieldDeclaration.modifiers().addAll(Arrays.asList(createPublicModifier(ast), createFinalModifier(ast)));
		fieldDeclaration.setType(createSimpleType(ast, field));
		return fieldDeclaration;
	}

	private Modifier createFinalModifier(final AST ast) {
		return ast.newModifier(ModifierKeyword.FINAL_KEYWORD);
	}

	private Modifier createPublicModifier(final AST ast) {
		return ast.newModifier(ModifierKeyword.PUBLIC_KEYWORD);
	}

	private MethodDeclaration getConstructor(final String name, final IField[] fields, final ASTRewrite astRewrite,
			final Collection<Modifier> modifiers) throws JavaModelException {
		final AST ast = astRewrite.getAST();
		final Block body = ast.newBlock();
		final Collection<SingleVariableDeclaration> parameters = new ArrayList<>();
		for (IField field : fields) {
			final FieldAccess access = ast.newFieldAccess();
			access.setExpression(ast.newThisExpression());
			access.setName(createSimpleName(ast, field));

			final Assignment assignment = ast.newAssignment();
			assignment.setLeftHandSide(access);
			assignment.setRightHandSide(createSimpleName(ast, field));
			assignment.setOperator(Assignment.Operator.ASSIGN);
			body.statements().add(ast.newExpressionStatement(assignment));

			final SingleVariableDeclaration parameter = ast.newSingleVariableDeclaration();
			parameter.setName(ast.newSimpleName(field.getElementName()));
			parameter.setType(createSimpleType(ast, field));
			if (codeGenerationSettings.makeParametersFinal) {
				parameter.modifiers().add(createFinalModifier(ast));
			}
			parameters.add(parameter);
		}

		final MethodDeclaration constructor = ast.newMethodDeclaration();
		constructor.setConstructor(true);
		constructor.modifiers().addAll(modifiers);
		constructor.setName(ast.newSimpleName(name));
		constructor.setBody(body);
		constructor.parameters().addAll(parameters);

		return constructor;
	}

	private String getType(final IField field) {
		return field.getDeclaringType().getTypeQualifiedName('.');
	}

	private void removeAccessor(final IMethod accessor, final ListRewrite rewrite) throws JavaModelException {
		if (accessor != null) {
			final MethodDeclaration declaration = (MethodDeclaration) getParent(
					NodeFinder.perform(rewrite.getParent().getRoot(), accessor.getNameRange()),
					MethodDeclaration.class);
			if (declaration != null) {
				rewrite.remove(declaration, null);
			}
		}
	}

	private IMethod findMethod(final String name, final IType type, final boolean isConstructor)
			throws JavaModelException {
		IMethod[] methods = type.getMethods();
		for (int i = 0; i < methods.length; i++) {
			if (isSameMethodSignature(name, new String[0], isConstructor, methods[i])) {
				return methods[i];
			}
		}
		return null;
	}

	private boolean isSameMethodSignature(final String name, final String[] paramTypes, final boolean isConstructor,
			final IMethod method) throws JavaModelException {
		if (isConstructor || name.equals(method.getElementName())) {
			if (isConstructor == method.isConstructor()) {
				String[] currParamTypes = method.getParameterTypes();
				if (paramTypes.length == currParamTypes.length) {
					for (int i = 0; i < paramTypes.length; i++) {
						if (!Signature.getSimpleName(Signature.toString(paramTypes[i]))
								.equals(Signature.getSimpleName(Signature.toString(currParamTypes[i])))) {
							return false;
						}
					}
					return true;
				}
			}
		}
		return false;
	}

	private boolean methodExists(final IType type, final MethodDeclaration declaration) throws JavaModelException {
		final IMethod[] methods = type.getMethods();
		if (methods != null) {
			final String methodToFindName = declaration.getName().toString();
			final List<SingleVariableDeclaration> methodToFindParams = declaration.parameters();
			for (IMethod method : methods) {
				if (methodToFindName.equals(method.getElementName())) {
					final String[] parameterTypes = method.getParameterTypes();
					if (methodToFindParams.size() == parameterTypes.length) {
						for (int i = 0; i < parameterTypes.length; i++) {
							if (!methodToFindParams.get(0).getName().toString().equals(parameterTypes)) {
								return false;
							}
						}
					}
				}
			}
			return true;
		}
		return false;
	}
}