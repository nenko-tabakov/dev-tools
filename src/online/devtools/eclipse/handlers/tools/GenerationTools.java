package online.devtools.eclipse.handlers.tools;

import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jdt.core.NamingConventions;
import org.eclipse.jdt.core.Signature;
import org.eclipse.jdt.ui.PreferenceConstants;

public final class GenerationTools {

	private GenerationTools() {
	}

	public static String getGetterName(IField field) throws JavaModelException {
		boolean useIs = useIsForBooleanGetters(field.getJavaProject());
		return getGetterName(field, useIs);
	}

	private static boolean useIsForBooleanGetters(IJavaProject project) {
		return Boolean.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_IS_FOR_GETTERS, project))
				.booleanValue();
	}

	public static String getGetterName(IField field, boolean useIsForBoolGetters) throws JavaModelException {
		return getGetterName(field.getJavaProject(), field.getElementName(), field.getFlags(),
				useIsForBoolGetters && isBoolean(field));
	}

	private static boolean isBoolean(IField field) throws JavaModelException {
		return field.getTypeSignature().equals(Signature.SIG_BOOLEAN);
	}

	private static String getGetterName(IJavaProject project, String fieldName, int flags, boolean isBoolean) {
		return NamingConventions.suggestGetterName(project, fieldName, flags, isBoolean, null);
	}

	public static CodeSettings getCodeGenerationSettings(IJavaProject project) {
		return CodeSettings.builder()
				.withAddComments(Boolean
						.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_ADD_COMMENTS, project))
						.booleanValue())
				.withIsForBooleanGetters(useIsForBooleanGetters(project))
				.withQualifyFieldAccessWithThis(Boolean
						.valueOf(PreferenceConstants.getPreference(PreferenceConstants.CODEGEN_KEYWORD_THIS, project))
						.booleanValue())
				.build();
	}

}
