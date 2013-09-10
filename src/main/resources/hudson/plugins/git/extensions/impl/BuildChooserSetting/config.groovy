package hudson.plugins.git.extensions.impl.BuildChooserSetting

import hudson.plugins.git.util.BuildChooserDescriptor;

def f = namespace(lib.FormTagLib);

f.dropdownDescriptorSelector(title:_("Choosing strategy"), descriptors: descriptor.getBuildChooserDescriptor(it))
