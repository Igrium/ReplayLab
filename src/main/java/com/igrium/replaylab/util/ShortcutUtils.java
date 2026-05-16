package com.igrium.replaylab.util;

import static imgui.flag.ImGuiKey.*;

import com.google.common.collect.ImmutableMap;
import imgui.flag.ImGuiKey;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import lombok.experimental.UtilityClass;
import net.minecraft.util.Language;

import java.util.Map;
import java.util.function.IntConsumer;

@UtilityClass
public final class ShortcutUtils {
    public static final Map<Integer, String> MOD_NAMES = ImmutableMap.of(
            ImGuiKey.ImGuiMod_None, "key.keyboard.unknown",
            ImGuiKey.ImGuiMod_Ctrl, "key.keyboard.left.control",
            ImGuiKey.ImGuiMod_Shift, "key.keyboard.left.shift",
            ImGuiKey.ImGuiMod_Alt, "key.keyboard.left.alt",
            ImGuiKey.ImGuiMod_Super, "key.keyboard.left.win"
    );

    public static String getModName(int modCode) {
        return Language.getInstance().get(MOD_NAMES.getOrDefault(modCode, "key.keyboard.unknown"));
    }

    public static final Map<Integer, String> KEY_NAMES = ImmutableMap.<Integer, String>builder()
            .put(ImGuiKey.None, "key.keyboard.unknown")
            // Navigation
            .put(ImGuiKey.LeftArrow, "key.keyboard.left")
            .put(ImGuiKey.RightArrow, "key.keyboard.right")
            .put(ImGuiKey.UpArrow, "key.keyboard.up")
            .put(ImGuiKey.DownArrow, "key.keyboard.down")
            .put(ImGuiKey.PageUp, "key.keyboard.page.up")
            .put(ImGuiKey.PageDown, "key.keyboard.page.down")
            .put(ImGuiKey.Home, "key.keyboard.home")
            .put(ImGuiKey.End, "key.keyboard.end")
            .put(ImGuiKey.Insert, "key.keyboard.insert")
            .put(ImGuiKey.Delete, "key.keyboard.delete")
            // Editing
            .put(ImGuiKey.Backspace, "key.keyboard.backspace")
            .put(ImGuiKey.Space, "key.keyboard.space")
            .put(ImGuiKey.Enter, "key.keyboard.enter")
            .put(ImGuiKey.Escape, "key.keyboard.escape")
            .put(ImGuiKey.Tab, "key.keyboard.tab")
            // Modifiers
            .put(ImGuiKey.LeftCtrl, "key.keyboard.left.control")
            .put(ImGuiKey.LeftShift, "key.keyboard.left.shift")
            .put(ImGuiKey.LeftAlt, "key.keyboard.left.alt")
            .put(ImGuiKey.LeftSuper, "key.keyboard.left.win")
            .put(ImGuiKey.RightCtrl, "key.keyboard.right.control")
            .put(ImGuiKey.RightShift, "key.keyboard.right.shift")
            .put(ImGuiKey.RightAlt, "key.keyboard.right.alt")
            .put(ImGuiKey.RightSuper, "key.keyboard.right.win")
            .put(ImGuiKey.Menu, "key.keyboard.menu")
            // Digits
            .put(ImGuiKey._0, "key.keyboard.0")
            .put(ImGuiKey._1, "key.keyboard.1")
            .put(ImGuiKey._2, "key.keyboard.2")
            .put(ImGuiKey._3, "key.keyboard.3")
            .put(ImGuiKey._4, "key.keyboard.4")
            .put(ImGuiKey._5, "key.keyboard.5")
            .put(ImGuiKey._6, "key.keyboard.6")
            .put(ImGuiKey._7, "key.keyboard.7")
            .put(ImGuiKey._8, "key.keyboard.8")
            .put(ImGuiKey._9, "key.keyboard.9")
            // Letters
            .put(ImGuiKey.A, "key.keyboard.a")
            .put(ImGuiKey.B, "key.keyboard.b")
            .put(ImGuiKey.C, "key.keyboard.c")
            .put(ImGuiKey.D, "key.keyboard.d")
            .put(ImGuiKey.E, "key.keyboard.e")
            .put(ImGuiKey.F, "key.keyboard.f")
            .put(ImGuiKey.G, "key.keyboard.g")
            .put(ImGuiKey.H, "key.keyboard.h")
            .put(ImGuiKey.I, "key.keyboard.i")
            .put(ImGuiKey.J, "key.keyboard.j")
            .put(ImGuiKey.K, "key.keyboard.k")
            .put(ImGuiKey.L, "key.keyboard.l")
            .put(ImGuiKey.M, "key.keyboard.m")
            .put(ImGuiKey.N, "key.keyboard.n")
            .put(ImGuiKey.O, "key.keyboard.o")
            .put(ImGuiKey.P, "key.keyboard.p")
            .put(ImGuiKey.Q, "key.keyboard.q")
            .put(ImGuiKey.R, "key.keyboard.r")
            .put(ImGuiKey.S, "key.keyboard.s")
            .put(ImGuiKey.T, "key.keyboard.t")
            .put(ImGuiKey.U, "key.keyboard.u")
            .put(ImGuiKey.V, "key.keyboard.v")
            .put(ImGuiKey.W, "key.keyboard.w")
            .put(ImGuiKey.X, "key.keyboard.x")
            .put(ImGuiKey.Y, "key.keyboard.y")
            .put(ImGuiKey.Z, "key.keyboard.z")
            // Function keys
            .put(ImGuiKey.F1, "key.keyboard.f1")
            .put(ImGuiKey.F2, "key.keyboard.f2")
            .put(ImGuiKey.F3, "key.keyboard.f3")
            .put(ImGuiKey.F4, "key.keyboard.f4")
            .put(ImGuiKey.F5, "key.keyboard.f5")
            .put(ImGuiKey.F6, "key.keyboard.f6")
            .put(ImGuiKey.F7, "key.keyboard.f7")
            .put(ImGuiKey.F8, "key.keyboard.f8")
            .put(ImGuiKey.F9, "key.keyboard.f9")
            .put(ImGuiKey.F10, "key.keyboard.f10")
            .put(ImGuiKey.F11, "key.keyboard.f11")
            .put(ImGuiKey.F12, "key.keyboard.f12")
            .put(ImGuiKey.F13, "key.keyboard.f13")
            .put(ImGuiKey.F14, "key.keyboard.f14")
            .put(ImGuiKey.F15, "key.keyboard.f15")
            .put(ImGuiKey.F16, "key.keyboard.f16")
            .put(ImGuiKey.F17, "key.keyboard.f17")
            .put(ImGuiKey.F18, "key.keyboard.f18")
            .put(ImGuiKey.F19, "key.keyboard.f19")
            .put(ImGuiKey.F20, "key.keyboard.f20")
            .put(ImGuiKey.F21, "key.keyboard.f21")
            .put(ImGuiKey.F22, "key.keyboard.f22")
            .put(ImGuiKey.F23, "key.keyboard.f23")
            .put(ImGuiKey.F24, "key.keyboard.f24")
            // Punctuation & symbols
            .put(ImGuiKey.Apostrophe, "key.keyboard.apostrophe")
            .put(ImGuiKey.Comma, "key.keyboard.comma")
            .put(ImGuiKey.Minus, "key.keyboard.minus")
            .put(ImGuiKey.Period, "key.keyboard.period")
            .put(ImGuiKey.Slash, "key.keyboard.slash")
            .put(ImGuiKey.Semicolon, "key.keyboard.semicolon")
            .put(ImGuiKey.Equal, "key.keyboard.equal")
            .put(ImGuiKey.LeftBracket, "key.keyboard.left.bracket")
            .put(ImGuiKey.Backslash, "key.keyboard.backslash")
            .put(ImGuiKey.RightBracket, "key.keyboard.right.bracket")
            .put(ImGuiKey.GraveAccent, "key.keyboard.grave.accent")
            // Lock & system keys
            .put(ImGuiKey.CapsLock, "key.keyboard.caps.lock")
            .put(ImGuiKey.ScrollLock, "key.keyboard.scroll.lock")
            .put(ImGuiKey.NumLock, "key.keyboard.num.lock")
            .put(ImGuiKey.PrintScreen, "key.keyboard.print.screen")
            .put(ImGuiKey.Pause, "key.keyboard.pause")
            // Keypad
            .put(ImGuiKey.Keypad0, "key.keyboard.keypad.0")
            .put(ImGuiKey.Keypad1, "key.keyboard.keypad.1")
            .put(ImGuiKey.Keypad2, "key.keyboard.keypad.2")
            .put(ImGuiKey.Keypad3, "key.keyboard.keypad.3")
            .put(ImGuiKey.Keypad4, "key.keyboard.keypad.4")
            .put(ImGuiKey.Keypad5, "key.keyboard.keypad.5")
            .put(ImGuiKey.Keypad6, "key.keyboard.keypad.6")
            .put(ImGuiKey.Keypad7, "key.keyboard.keypad.7")
            .put(ImGuiKey.Keypad8, "key.keyboard.keypad.8")
            .put(ImGuiKey.Keypad9, "key.keyboard.keypad.9")
            .put(ImGuiKey.KeypadDecimal, "key.keyboard.keypad.decimal")
            .put(ImGuiKey.KeypadDivide, "key.keyboard.keypad.divide")
            .put(ImGuiKey.KeypadMultiply, "key.keyboard.keypad.multiply")
            .put(ImGuiKey.KeypadSubtract, "key.keyboard.keypad.subtract")
            .put(ImGuiKey.KeypadAdd, "key.keyboard.keypad.add")
            .put(ImGuiKey.KeypadEnter, "key.keyboard.keypad.enter")
            .put(ImGuiKey.KeypadEqual, "key.keyboard.keypad.equal")
            .build();

    public static String getKeyName(int keyCode) {
        return Language.getInstance().get(KEY_NAMES.get(keyCode), "key.keyboard.unknown");
    }

    /**
     * Get the ImGuiKey component of a key chord
     * @param chord Full key chord
     * @return The ImGuiKey component of the chord
     */
    public static int getChordKey(int chord) {
        return chord & ~ImGuiKey.ImGuiMod_Mask_;
    }

    /**
     * Trigger a consumer for every modifier key in a chord.
     * @param chord Full key chord
     * @param consumer Accepts the ImGuiMod component of the chord.
     */
    public static void forChordMods(int chord, IntConsumer consumer) {
        if (hasFlag(chord, ImGuiMod_Ctrl)) consumer.accept(ImGuiMod_Ctrl);
        if (hasFlag(chord, ImGuiMod_Shift)) consumer.accept(ImGuiMod_Shift);
        if (hasFlag(chord, ImGuiMod_Alt)) consumer.accept(ImGuiMod_Alt);
        if (hasFlag(chord, ImGuiMod_Alt)) consumer.accept(ImGuiMod_Alt);
        if (hasFlag(chord, ImGuiMod_Super)) consumer.accept(ImGuiMod_Super);
    }

    /**
     * Get all the modifiers in a chord.
     * @param chord Full key chord.
     * @return Individual ImGuiMod components of the chord
     */
    public static int[] getChordMods(int chord) {
        IntList mods = new IntArrayList(5);
        forChordMods(chord, mods::add);
        return mods.toIntArray();
    }

    private static boolean hasFlag(int flags, int flag) {
        return (flags & flag) == flag;
    }
}