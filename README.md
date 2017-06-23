# Dev Tools
Eclipse Plug-in for converting existing classes to immutable ones.

## How to install
Use the update site
After installation when the Java Editor is active a new menu item (**Convert**) will be available

## How to use

### Common steps
1. Create class
2. Add fields
3. From the menu select Convert -> Generate Immutable POJO
4. Select appropriate options and click OK

### Available options
 - **Generate method comments** - If selected will generate comments for the generated method based on the predefined Comments Templates
 - **Do not replace existing declarations** - if selected all methods that alredy exists but should be generated will be kept
 - **Add final modifier for parameters** - if selected the parameters of the generated methods and constructors will be marked `final`
 - **Generate public final fields instead of getters** - By default the fields will be marked final and getters will be generated for them. If this option is selected no getters will be generated and the fields will be marked `public final`
 - **Generate builder instead of public constructor** - By default a public constructor with parameters for each field will be generated. If this option is selected a private constructor will be generated and static inner builder class. This is generally useful if there are way too many fields.
