package helloworld;

import io.dropwizard.views.View;


public class HelloWorldView extends View {
    protected HelloWorldView(String templateName) {
        super("HelloWorld.ftl");
    }
}
