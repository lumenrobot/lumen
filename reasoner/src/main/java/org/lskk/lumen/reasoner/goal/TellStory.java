package org.lskk.lumen.reasoner.goal;

import org.lskk.lumen.reasoner.story.Story;

/**
 * Goal that tells a story to a person.
 * Created by ceefour on 07/11/2015.
 */
public class TellStory extends Goal {
    private String storyId;
    private Story story;
    private Integer lastPropositionIndex;
    private boolean done;

    public String getStoryId() {
        return storyId;
    }

    public void setStoryId(String storyId) {
        this.storyId = storyId;
    }

    public Story getStory() {
        return story;
    }

    public void setStory(Story story) {
        this.story = story;
    }

    /**
     * Last proposition that was uttered.
     * @return
     */
    public Integer getLastPropositionIndex() {
        return lastPropositionIndex;
    }

    public void setLastPropositionIndex(Integer lastPropositionIndex) {
        this.lastPropositionIndex = lastPropositionIndex;
    }

    public boolean isDone() {
        return done;
    }

    public void setDone(boolean done) {
        this.done = done;
    }

    @Override
    public String toString() {
        return "TellStory{" +
                "storyId='" + storyId + '\'' +
                ", story=" + story +
                ", lastPropositionIndex=" + lastPropositionIndex +
                ", done=" + done +
                '}';
    }
}
