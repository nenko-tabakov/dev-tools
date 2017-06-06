package online.devtools.eclipse.handlers.tools;

public class CodeSettings {

	public final boolean useIsForBooleanGetters;
	public final boolean qualifyFieldAccessWithThis;
	public final boolean addComments;

	private CodeSettings(final boolean useIsForBooleanGetters, final boolean qualifyFieldAccessWithThis,
			final boolean addComments) {
		this.useIsForBooleanGetters = useIsForBooleanGetters;
		this.qualifyFieldAccessWithThis = qualifyFieldAccessWithThis;
		this.addComments = addComments;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private boolean useIsForBooleanGetters;
		private boolean qualifyFieldAccessWithThis;
		private boolean addComments;

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

		public CodeSettings build() {
			return new CodeSettings(useIsForBooleanGetters, qualifyFieldAccessWithThis, addComments);
		}
	}
}
