package net.onebeastchris.geyser.extension.pickpack;

import net.onebeastchris.geyser.extension.pickpack.util.LanguageManager;
import net.onebeastchris.geyser.extension.pickpack.util.PackEntry;
import net.onebeastchris.geyser.extension.pickpack.util.SubPack;
import org.geysermc.cumulus.form.SimpleForm;
import org.geysermc.geyser.api.connection.GeyserConnection;
import org.geysermc.geyser.api.pack.ResourcePackManifest;
import org.geysermc.geyser.session.GeyserSession;
import org.geysermc.geyser.text.ChatColor;

import java.util.*;

import static net.onebeastchris.geyser.extension.pickpack.PickPack.config;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.loader;
import static net.onebeastchris.geyser.extension.pickpack.PickPack.storage;

public class Form {
    private final String lang;
    private final GeyserConnection connection;
    private boolean changesMade = false;

    public Form(GeyserConnection connection) {
        this.connection = connection;
        this.lang = connection.locale();
    }

    public Form(GeyserConnection connection, boolean changesMade) {
        this.connection = connection;
        this.lang = connection.locale();
        this.changesMade = changesMade;
    }

    public void send(String... args) {
        if (args != null && args.length > 0) {
            switch (args[0].toLowerCase()) {
                case "settings", "setting" -> {
                    settingsForm();
                    return;
                }
                case "clear", "reset", "default" -> {
                    resetConfirmForm();
                    return;
                }
            }
        }
        sendMenu();
    }

    public void sendMenu() {
        String xuid = connection.xuid();
        List<PackEntry> selectedEntries = storage.getPackEntries(xuid);
        Set<String> selectedUuids = new HashSet<>();
        for (PackEntry e : selectedEntries) {
            selectedUuids.add(e.uuid());
        }

        SimpleForm.Builder form = SimpleForm.builder()
                .title(LanguageManager.getLocaleString(lang, "main.menu.title"));

        List<Runnable> buttonActions = new ArrayList<>();

        // Section 1: Selected Packs
        form.button("\u00A7l" + LanguageManager.getLocaleString(lang, "pack.selected.title") + "\u00A7r");
        buttonActions.add(this::sendMenu);

        for (int i = 0; i < selectedEntries.size(); i++) {
            PackEntry entry = selectedEntries.get(i);
            String name = getPackName(entry.uuid());
            String subpackTag = "";
            if (!entry.subpack().isEmpty()) {
                subpackTag = " \u00A77[" + getSubpackDisplayName(entry.uuid(), entry.subpack()) + "]\u00A7r";
            }
            form.button(name + subpackTag);
            final int pos = i;
            buttonActions.add(() -> packManagerMenu(entry.uuid(), true, pos, selectedEntries.size()));
        }

        // Section 2: Available Packs
        form.button("\u00A7l" + LanguageManager.getLocaleString(lang, "pack.available.title") + "\u00A7r");
        buttonActions.add(this::sendMenu);

        for (Map.Entry<String, ResourcePackManifest> packEntry : loader.PACKS_INFO.entrySet()) {
            if (!selectedUuids.contains(packEntry.getKey())) {
                String name = packEntry.getValue().header().name();
                form.button(name);
                final String uuid = packEntry.getKey();
                buttonActions.add(() -> packManagerMenu(uuid, false, -1, selectedEntries.size()));
            }
        }

        // Settings button in bold style like "Selected Packs" and "Available Packs"
        form.button("\u00A7l" + LanguageManager.getLocaleString(lang, "main.menu.settings") + "\u00A7r");
        buttonActions.add(this::settingsForm);

        // Section 3: Reset all to default button (in red at the bottom)
        form.button("\u00A7c" + LanguageManager.getLocaleString(lang, "main.menu.reset") + "\u00A7r");
        buttonActions.add(this::resetConfirmForm);

        // When closed or dismissed with 'X', auto-reload if changes were made and auto-reload is enabled
        form.closedOrInvalidResultHandler((simpleForm, response) -> onMainMenuClosed(xuid));

        form.validResultHandler((simpleForm, response) -> {
            int clicked = response.clickedButtonId();
            if (clicked >= 0 && clicked < buttonActions.size()) {
                buttonActions.get(clicked).run();
            } else {
                onMainMenuClosed(xuid);
            }
        });

        connection.sendForm(form.build());
    }

    private void onMainMenuClosed(String xuid) {
        if (changesMade) {
            if (storage.useTransferPacket(xuid)) {
                GeyserSession session = (GeyserSession) connection;
                session.transfer(config.address(), config.port());
            } else {
                connection.sendMessage(LanguageManager.getLocaleString(lang, "changes.saved.reconnect"));
            }
        }
    }

    public void packManagerMenu(String uuid, boolean isSelected, int pos, int totalSelected) {
        String xuid = connection.xuid();
        String name = getPackName(uuid);
        String description = getPackDescription(uuid);
        boolean showDesc = storage.showPackDescription(xuid);

        SimpleForm.Builder form = SimpleForm.builder().title(name);

        StringBuilder info = new StringBuilder();
        if (showDesc && !description.isEmpty()) {
            info.append(ChatColor.ITALIC).append(description).append(ChatColor.RESET).append("\n\n");
        }
        ResourcePackManifest manifest = loader.PACKS_INFO.get(uuid);
        if (manifest != null && manifest.header() != null) {
            info.append("Version: ").append(manifest.header().version()).append("\n");
        }

        if (isSelected && loader.hasSubpacks(uuid)) {
            String currentSubpack = storage.getSelectedSubpack(xuid, uuid);
            info.append("Subpack: ").append(getSubpackDisplayName(uuid, currentSubpack)).append("\n");
        }
        form.content(info.toString());

        List<Runnable> buttonActions = new ArrayList<>();

        if (!isSelected) {
            // Select pack button
            form.button(LanguageManager.getLocaleString(lang, "pack.action.select"),
                    org.geysermc.cumulus.util.FormImage.Type.PATH, "textures/ui/plus.png");
            buttonActions.add(() -> {
                changesMade = true;
                List<PackEntry> current = new ArrayList<>(storage.getPackEntries(xuid));
                current.add(0, new PackEntry(uuid, ""));
                storage.setPackEntries(xuid, current).thenRun(this::sendMenu);
            });
        } else {
            // Move Up (if not first)
            if (pos > 0) {
                form.button(LanguageManager.getLocaleString(lang, "pack.action.move_up"),
                        org.geysermc.cumulus.util.FormImage.Type.PATH, "textures/ui/up_arrow.png");
                buttonActions.add(() -> {
                    changesMade = true;
                    storage.movePack(xuid, pos, pos - 1).thenRun(this::sendMenu);
                });
            }

            // Remove button
            form.button(LanguageManager.getLocaleString(lang, "pack.action.remove"),
                    org.geysermc.cumulus.util.FormImage.Type.PATH, "textures/ui/minus.png");
            buttonActions.add(() -> {
                changesMade = true;
                List<PackEntry> current = new ArrayList<>(storage.getPackEntries(xuid));
                if (pos >= 0 && pos < current.size()) {
                    current.remove(pos);
                    storage.setPackEntries(xuid, current).thenRun(this::sendMenu);
                }
            });

            // Move Down (if not last)
            if (pos < totalSelected - 1) {
                form.button(LanguageManager.getLocaleString(lang, "pack.action.move_down"),
                        org.geysermc.cumulus.util.FormImage.Type.PATH, "textures/ui/down_arrow.png");
                buttonActions.add(() -> {
                    changesMade = true;
                    storage.movePack(xuid, pos, pos + 1).thenRun(this::sendMenu);
                });
            }

            // Subpack configuration button (if applicable)
            if (loader.hasSubpacks(uuid)) {
                form.button(LanguageManager.getLocaleString(lang, "pack.action.change_subpack"),
                        org.geysermc.cumulus.util.FormImage.Type.PATH, "textures/ui/settings_glyph_color_2x.png");
                buttonActions.add(() -> subpackPage(uuid));
            }
        }

        // Back button
        form.button(LanguageManager.getLocaleString(lang, "pack.details.back"));
        buttonActions.add(this::sendMenu);

        form.closedOrInvalidResultHandler((simpleForm, response) -> sendMenu());

        form.validResultHandler((simpleForm, response) -> {
            int clicked = response.clickedButtonId();
            if (clicked >= 0 && clicked < buttonActions.size()) {
                buttonActions.get(clicked).run();
            } else {
                sendMenu();
            }
        });

        connection.sendForm(form.build());
    }

    public void subpackPage(String uuid) {
        String xuid = connection.xuid();
        String name = getPackName(uuid);
        String description = getPackDescription(uuid);
        String currentSubpack = storage.getSelectedSubpack(xuid, uuid);
        List<SubPack> subpacks = loader.getSubpacks(uuid);

        SimpleForm.Builder form = SimpleForm.builder()
                .title(String.format(LanguageManager.getLocaleString(lang, "subpack.form.title"), name));

        StringBuilder content = new StringBuilder();
        if (!description.isEmpty()) {
            content.append(ChatColor.ITALIC).append(description).append(ChatColor.RESET).append("\n\n");
        }
        content.append(String.format(LanguageManager.getLocaleString(lang, "pack.details.current_subpack"),
                getSubpackDisplayName(uuid, currentSubpack)));
        content.append("\n").append(LanguageManager.getLocaleString(lang, "pack.details.select_subpack"));
        form.content(content.toString());

        List<Runnable> actions = new ArrayList<>();

        // Option 0: Default/None
        boolean isDefaultActive = currentSubpack.isEmpty();
        String defaultLabel = LanguageManager.getLocaleString(lang, "subpack.default");
        form.button(isDefaultActive ? String.format(LanguageManager.getLocaleString(lang, "subpack.active"), defaultLabel) : defaultLabel);
        actions.add(() -> {
            changesMade = true;
            storage.setPackSubpack(xuid, uuid, "").thenRun(this::sendMenu);
        });

        for (SubPack sp : subpacks) {
            boolean isActive = sp.folderName().equalsIgnoreCase(currentSubpack);
            String btnText = isActive ? String.format(LanguageManager.getLocaleString(lang, "subpack.active"), sp.name()) : sp.name();
            form.button(btnText);
            actions.add(() -> {
                changesMade = true;
                storage.setPackSubpack(xuid, uuid, sp.folderName()).thenRun(this::sendMenu);
            });
        }

        form.button(LanguageManager.getLocaleString(lang, "pack.details.back"));
        actions.add(this::sendMenu);

        form.closedOrInvalidResultHandler((simpleForm, response) -> sendMenu());

        form.validResultHandler((simpleForm, response) -> {
            int clicked = response.clickedButtonId();
            if (clicked >= 0 && clicked < actions.size()) {
                actions.get(clicked).run();
            } else {
                sendMenu();
            }
        });

        connection.sendForm(form.build());
    }

    public void settingsForm() {
        String xuid = connection.xuid();
        boolean showDesc = storage.showPackDescription(xuid);
        boolean autoReload = storage.useTransferPacket(xuid);

        org.geysermc.cumulus.form.CustomForm.Builder form = org.geysermc.cumulus.form.CustomForm.builder()
                .title(LanguageManager.getLocaleString(lang, "settings.form.title"))
                .label(LanguageManager.getLocaleString(lang, "settings.description.info"))
                .toggle(LanguageManager.getLocaleString(lang, "settings.description.name"), showDesc)
                .label(LanguageManager.getLocaleString(lang, "settings.reload.info"))
                .toggle(LanguageManager.getLocaleString(lang, "settings.reload.name"), autoReload);

        form.closedOrInvalidResultHandler((customForm, response) -> sendMenu());

        form.validResultHandler((customForm, response) -> {
            response.includeLabels(true);
            // Index 0: label (description info)
            // Index 1: toggle (show pack descriptions)
            // Index 2: label (reload info)
            // Index 3: toggle (automatically reload packs)
            boolean newShowDesc = response.asToggle(1);
            boolean newAutoReload = response.asToggle(3);

            storage.setShowPackDescription(xuid, newShowDesc);
            storage.setUseTransferPacket(xuid, newAutoReload);
            sendMenu();
        });

        connection.sendForm(form.build());
    }

    public void resetConfirmForm() {
        String xuid = connection.xuid();
        SimpleForm.Builder form = SimpleForm.builder()
                .title(LanguageManager.getLocaleString(lang, "reset.confirm.title"))
                .content(LanguageManager.getLocaleString(lang, "reset.confirm.content"));

        List<Runnable> actions = new ArrayList<>();

        // Confirm button
        form.button("\u00A7a" + LanguageManager.getLocaleString(lang, "reset.confirm.yes") + "\u00A7r");
        actions.add(() -> {
            changesMade = true;
            storage.reset(xuid).thenRun(() -> {
                connection.sendMessage(LanguageManager.getLocaleString(lang, "reset.success"));
                if (storage.useTransferPacket(xuid)) {
                    GeyserSession session = (GeyserSession) connection;
                    session.transfer(config.address(), config.port());
                } else {
                    sendMenu();
                }
            });
        });

        // Cancel button
        form.button("\u00A7c" + LanguageManager.getLocaleString(lang, "reset.confirm.cancel") + "\u00A7r");
        actions.add(this::sendMenu);

        form.closedOrInvalidResultHandler((simpleForm, response) -> sendMenu());

        form.validResultHandler((simpleForm, response) -> {
            int clicked = response.clickedButtonId();
            if (clicked >= 0 && clicked < actions.size()) {
                actions.get(clicked).run();
            }
        });

        connection.sendForm(form.build());
    }

    private String getPackName(String uuid) {
        ResourcePackManifest manifest = loader.PACKS_INFO.get(uuid);
        if (manifest != null && manifest.header() != null) {
            return manifest.header().name();
        }
        return uuid;
    }

    private String getPackDescription(String uuid) {
        ResourcePackManifest manifest = loader.PACKS_INFO.get(uuid);
        if (manifest != null && manifest.header() != null && manifest.header().description() != null) {
            return manifest.header().description();
        }
        return "";
    }

    private String getSubpackDisplayName(String uuid, String folderName) {
        if (folderName == null || folderName.isEmpty()) {
            return LanguageManager.getLocaleString(lang, "subpack.none");
        }
        SubPack sp = loader.getSubpack(uuid, folderName);
        if (sp != null) {
            return sp.name();
        }
        return folderName;
    }
}
