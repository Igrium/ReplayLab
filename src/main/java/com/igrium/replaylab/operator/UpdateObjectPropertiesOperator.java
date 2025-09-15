package com.igrium.replaylab.operator;

import com.google.gson.JsonObject;
import com.igrium.replaylab.anim.AnimationObject;
import com.igrium.replaylab.scene.ReplayScene;

public class UpdateObjectPropertiesOperator implements ReplayOperator {

    private final AnimationObject subject;

    private JsonObject pre;
    private JsonObject post;

    public UpdateObjectPropertiesOperator(AnimationObject subject) {
        this.subject = subject;
    }

    @Override
    public boolean execute(ReplayScene scene) {
        pre = subject.getPrevSavedProperties();
        post = new JsonObject();

        subject.writeJson(post);
        subject.setPrevSavedProperties(post);

        return true;
    }

    @Override
    public void undo(ReplayScene scene) {
        subject.readJson(pre);
    }

    @Override
    public void redo(ReplayScene scene) {
        subject.readJson(post);
    }
}
