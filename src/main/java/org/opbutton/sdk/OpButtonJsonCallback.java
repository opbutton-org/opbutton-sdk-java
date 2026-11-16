package org.opbutton.sdk;

@FunctionalInterface
public interface OpButtonJsonCallback {
    void onAction(String identifier, OpButtonAction action);
}
