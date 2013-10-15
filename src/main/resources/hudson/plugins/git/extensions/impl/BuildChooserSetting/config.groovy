package hudson.plugins.git.extensions.impl.BuildChooserSetting

def f = namespace(lib.FormTagLib);

f.entry() {
    f.dropdownDescriptorSelector(title:_("Choosing strategy"), field:"buildChooser",
            descriptors: descriptor.getBuildChooserDescriptors(my))
}