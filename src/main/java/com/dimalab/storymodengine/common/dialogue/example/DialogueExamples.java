package com.dimalab.storymodengine.common.dialogue.example;

import com.dimalab.storymodengine.common.StoryModEngine;
import com.dimalab.storymodengine.common.dialogue.DialogueDefinition;
import com.dimalab.storymodengine.common.dialogue.DialogueLine;
import com.dimalab.storymodengine.api.dialogue.DialogueMode;
import com.dimalab.storymodengine.common.dialogue.DialogueSpeaker;
import com.dimalab.storymodengine.common.dialogue.DialogueSpeakerRegistry;
import com.dimalab.storymodengine.common.dialogue.DialogueWindowAnimation;
import com.dimalab.storymodengine.api.dialogue.annotation.AutoDialogue;
import net.minecraft.resources.ResourceLocation;

/**
 * The required demonstration dialogues, built with the plain Java DSL — {@code @AutoDialogue}
 * registers each automatically (proof by construction, the same way {@code FlowDemoScenario.DEMO}
 * proves {@code @AutoFlow}), no manual {@code DialogueRegistry.register} call anywhere.
 */
public final class DialogueExamples {

    /** A raw texture file path (note the {@code .png}), not an item model id — {@code GuiGraphics#blit} needs the actual file. */
    private static final String PLACEHOLDER_AVATAR = "minecraft:textures/item/diamond.png";

    static {
        // No portrait registered here — this demo ships no art asset; DialoguePresenter falls back
        // to the raw speaker id + DialogueStyle's default color when a DialogueSpeaker (or its
        // portrait specifically) isn't registered, which is a fully supported, expected case. The
        // ENCOUNTER dialogue below demonstrates an avatar by setting one directly on a DialogueLine
        // instead (a vanilla item texture, clearly a placeholder — this engine ships no portrait art).
        DialogueSpeakerRegistry.register(new DialogueSpeaker("npc", "The Stranger", 0xFF66CCFF));
        DialogueSpeakerRegistry.register(new DialogueSpeaker("merchant", "Old Merchant", 0xFFDDAA33));
        DialogueSpeakerRegistry.register(new DialogueSpeaker("sphinx", "The Sphinx", 0xFFAA66FF));
    }

    @AutoDialogue
    public static final DialogueDefinition VILLAGE_GUARD = DialogueDefinition.builder(id("village_guard"))
            .title("Village Guard")
            .node("start")
                .line("Guard", "Halt! Who are you?")
                .choice("I'm a traveller.").gotoNode("traveller")
                .choice("None of your business.").gotoNode("rude")
            .node("traveller")
                .line("Guard", "Then welcome to the village. Mind the wolves after dark.")
                .end()
            .node("rude")
                .line("Guard", "Then move along, stranger.")
                .end()
            .build();

    /**
     * The visual showcase — every {@link DialogueWindow}/{@link DialogueMode}/{@link
     * DialogueWindowAnimation} feature this round added, back to back: a plain line, one with an
     * avatar, a deliberately long word-wrapping line, a {@code THOUGHT} (the task's own example —
     * an internal thought attributed to the player), a {@code WHISPER}, a {@code SHOUT}, an
     * explicit {@code SLIDE} entrance (everything else defaults to its style's {@code FADE}), then
     * several short lines in a row before the existing three-option choice.
     */
    @AutoDialogue
    public static final DialogueDefinition ENCOUNTER = DialogueDefinition.builder(id("encounter"))
            .title("The Encounter")
            .node("start")
                .line("npc", "Ты всё-таки пришёл.")
                .line(DialogueLine.builder("npc", "Смотри — вот мой знак.")
                        .portrait(PLACEHOLDER_AVATAR)
                        .nameColor(0xFFFF5555)
                        .build())
                .line("npc", "Я ждал тебя дольше, чем ты думаешь — с той самой ночи, когда всё это "
                        + "началось, я не переставал следить за дорогой, надеясь, что рано или поздно ты всё же появишься здесь.")
                .line(DialogueLine.builder("Player", "Я не мог оставить это так.").mode(DialogueMode.THOUGHT).build())
                .line(DialogueLine.builder("npc", "Тише... нас могут услышать.").mode(DialogueMode.WHISPER).build())
                .line(DialogueLine.builder("npc", "ТЫ ХОТЬ ПОНИМАЕШЬ, ЧТО НАДЕЛАЛ?!").mode(DialogueMode.SHOUT).build())
                .line(DialogueLine.builder("npc", "...Прости. Не сдержался.").mode(DialogueMode.MUTTER).build())
                .line(DialogueLine.builder("npc", "Тогда ответь мне...").animation(DialogueWindowAnimation.SLIDE).build())
                .choice("Помочь ему").gotoNode("help")
                .choice("Отказаться").gotoNode("leave")
                .choice("Спросить подробнее").gotoNode("ask")
            .node("help")
                .line("npc", "Хорошо. Тогда идём.")
                .end()
            .node("leave")
                .line("npc", "Понятно...")
                .end()
            .node("ask")
                .line("npc", "Что именно произошло?")
                .end()
            .build();

    /**
     * A two-level branch, unlike {@link #VILLAGE_GUARD}'s single fork — {@code "price"} itself
     * offers a further choice ({@code "haggle"}), and two different choices from two different
     * nodes ({@code "price"} and {@code "haggle"}) both converge back on the same {@code "deal"}
     * ending, demonstrating that a target node doesn't need to be reached from only one place.
     */
    @AutoDialogue
    public static final DialogueDefinition MERCHANT = DialogueDefinition.builder(id("merchant"))
            .title("The Merchant")
            .node("start")
                .line("merchant", "Товар хороший, но задёшево не отдам.")
                .choice("Сколько ты хочешь?").gotoNode("price")
                .choice("Слишком дорого, я пошёл.").gotoNode("leave")
            .node("price")
                .line("merchant", "Пятьдесят монет — и ни медяком меньше.")
                .choice("По рукам.").gotoNode("deal")
                .choice("Может, скинешь цену?").gotoNode("haggle")
                .choice("Нет, это слишком много.").gotoNode("leave")
            .node("haggle")
                .line("merchant", "Хмм... ладно, тридцать пять — и только потому что ты мне понравился.")
                .choice("Согласен.").gotoNode("deal")
                .choice("Всё равно дорого.").gotoNode("leave")
            .node("deal")
                .line("merchant", "Прекрасный выбор! Забирай, не пожалеешь.")
                .end()
            .node("leave")
                .line("merchant", "Как знаешь. Другой покупатель найдётся.")
                .end()
            .build();

    /**
     * A wrong-answer branch that doesn't dead-end but loops back into a *second* choice ({@code
     * "dontknow"} offers its own two options) rather than just ending — and both {@code "start"}
     * and {@code "dontknow"} share the same {@code "correct"}/{@code "wrong"} outcome nodes, the
     * same converging-targets idea as {@link #MERCHANT} taken one step further. Also mixes {@code
     * SHOUT}/{@code WHISPER} modes into a choice-driven dialogue, not just the linear {@link
     * #ENCOUNTER} showcase.
     */
    @AutoDialogue
    public static final DialogueDefinition RIDDLE = DialogueDefinition.builder(id("riddle"))
            .title("The Sphinx's Riddle")
            .node("start")
                .line("sphinx", "Ответь мне, путник: кто ходит на четырёх ногах утром, на двух — днём, и на трёх — вечером?")
                .choice("Человек").gotoNode("correct")
                .choice("Паук").gotoNode("wrong")
                .choice("Не знаю").gotoNode("dontknow")
            .node("correct")
                .line("sphinx", "Верно. Проходи, путник.")
                .end()
            .node("wrong")
                .line(DialogueLine.builder("sphinx", "НЕВЕРНО!").mode(DialogueMode.SHOUT).build())
                .line("sphinx", "Ты не пройдёшь.")
                .end()
            .node("dontknow")
                .line(DialogueLine.builder("sphinx", "Тогда подумай ещё раз...").mode(DialogueMode.WHISPER).build())
                .choice("Человек").gotoNode("correct")
                .choice("Сдаюсь").gotoNode("wrong")
            .build();

    private DialogueExamples() {
    }

    private static ResourceLocation id(String path) {
        return new ResourceLocation(StoryModEngine.MODID, path);
    }
}
