package online.devtools.eclipse.handlers.tools;

public class CodeSettings {

	public final boolean useIsForBooleanGetters;
	public final boolean qualifyFieldAccessWithThis;
	public final boolean addComments;
	public final boolean replaceExistingDeclarations;
	public final boolean makeParametersFinal;
	public final boolean generateFinalFields;
	public final boolean generateBuilder;

	private CodeSettings(final boolean useIsForBooleanGetters, final boolean qualifyFieldAccessWithThis,
			final boolean addComments, final boolean replaceExistingDeclarations, final boolean makeParametersFinal,
			boolean generateFinalFields, boolean generateBuilder) {
		this.useIsForBooleanGetters = useIsForBooleanGetters;
		this.qualifyFieldAccessWithThis = qualifyFieldAccessWithThis;
		this.addComments = addComments;
		this.replaceExistingDeclarations = replaceExistingDeclarations;
		this.makeParametersFinal = makeParametersFinal;
		this.generateFinalFields = generateFinalFields;
		this.generateBuilder = generateBuilder;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {
		private boolean useIsForBooleanGetters;
		private boolean qualifyFieldAccessWithThis;
		private boolean addComments;
		private boolean replaceExistingDeclarations;
		private boolean makeParametersFinal;
		private boolean generateFinalFields;
		private boolean generateBuilder;

		private Builder() {

		}

		public Builder withIsForBooleanGetters(boolean enabled) {
			this.useIsForBooleanGetters = enabled;
			return this;
		}

		public Builder withQualifyFieldAccessWithThis(boolean enabled) {
			this.qualifyFieldAccessWithThis = enabled;
			return this;
		}

		public Builder withAddComments(boolean enabled) {
			this.addComments = enabled;
			return this;
		}

		public Builder withReplaceExistingDeclarations(boolean enabled) {
			this.replaceExistingDeclarations = enabled;
			return this;
		}

		public Builder withMakeParametersFinal(boolean enabled) {
			this.makeParametersFinal = enabled;
			return this;
		}

		public Builder withGenerateFinalFields(boolean enabled) {
			this.generateFinalFields = enabled;
			return this;
		}

		public Builder withGenerateBuilder(boolean enabled) {
			this.generateBuilder = enabled;
			return this;
		}

		public Builder withCodeSettings(CodeSettings codeSettings) {
			this.useIsForBooleanGetters = codeSettings.useIsForBooleanGetters;
			this.qualifyFieldAccessWithThis = codeSettings.qualifyFieldAccessWithThis;
			this.addComments = codeSettings.addComments;
			this.replaceExistingDeclarations = codeSettings.replaceExistingDeclarations;
			this.makeParametersFinal = codeSettings.makeParametersFinal;
			this.generateFinalFields = codeSettings.generateFinalFields;
			this.generateBuilder = codeSettings.generateBuilder;

			return this;
		}

		public CodeSettings build() {
			return new CodeSettings(useIsForBooleanGetters, qualifyFieldAccessWithThis, addComments,
					replaceExistingDeclarations, makeParametersFinal, generateFinalFields, generateBuilder);
		}
	}
}