# Dev Tools
Eclipse Plug-in for converting existing classes to immutable ones.

## How to install

### Using Update Site
1. Download the [update site](https://github.com/nenko-tabakov/dev-tools/releases/download/InitialRelease/devtools-update.zip)
2. Extract on the file system
3. Start Eclipse
4. From the menu select Help -> Install New Software...
5. From the open window click on Add...
6. Click Local and navigate to the extracted update site. Don't forget to give it a name.
7. Follow the on-screen instructions

### Using Feature
1. Download the zipped [feature](https://github.com/nenko-tabakov/dev-tools/releases/download/InitialRelease/devtools-feature.zip)
2. Start Eclipse
3. From the menu select Help -> Install New Software...
4. From the open window click on Add...
5. Click Archive and navigate to the extracted update site.
6. After selecting the update size archive deselect "Group items by category"
7. Follow the on-screen instructions
 
After installation when the Java Editor is active a new menu item (**Convert**) will be available

## How to use

### Common steps
1. Create class
2. Add fields
3. From the menu select Convert -> To Immutable...
4. Select appropriate options and click OK

### Available options
 - **Generate method comments** - If selected this options will generate comments for the generated method based on the predefined Comments Templates
 - **Do not replace existing declarations** - if selected this option will keep existing methods that should be generated
 - **Add final modifier for parameters** - if selected the parameters of the generated methods and constructors will be marked `final`
 - **Generate public final fields instead of getters** - By default the fields will be marked final and getters will be generated for them. If this option is selected no getters will be generated and the fields will be marked `public final`
 - **Generate builder instead of public constructor** - By default a public constructor with parameters for each field will be generated. If this option is selected a private constructor will be generated and static inner builder class. This is generally useful if there are way too many fields.
