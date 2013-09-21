package hudson.plugins.git.extensions.impl.BuildChooserSetting

def f = namespace(lib.FormTagLib);

f.entry(title:_("Choosing strategy"), field:"buildChooser") {
    f.dropdownDescriptorSelector(descriptors: descriptor.getBuildChooserDescriptors(it))
}