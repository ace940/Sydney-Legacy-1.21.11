package me.aidan.sydney.mixins.accessors;

import net.minecraft.text.ClickEvent;
import net.minecraft.text.HoverEvent;
import net.minecraft.text.Style;
import net.minecraft.text.StyleSpriteSource;
import net.minecraft.text.TextColor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

import javax.annotation.Nullable;

@Mixin(Style.class)
public interface StyleAccessor {
    // 1.21.9 made the style font a StyleSpriteSource instead of a plain Identifier.
    @Invoker("<init>")
    static Style create(@Nullable TextColor color, @Nullable Integer shadowColor, @Nullable Boolean bold, @Nullable Boolean italic, @Nullable Boolean underlined, @Nullable Boolean strikethrough, @Nullable Boolean obfuscated, @Nullable ClickEvent clickEvent, @Nullable HoverEvent hoverEvent, @Nullable String insertion, @Nullable StyleSpriteSource font) {
        return null;
    }
}