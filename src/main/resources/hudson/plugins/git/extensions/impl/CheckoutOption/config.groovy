package hudson.plugins.git.extensions.impl.CheckoutOption;

def f = namespace(lib.FormTagLib);

f.entry(title:_("Timeout (in minutes) for checkout operation"), field:"timeout") {
    f.textbox()
}
