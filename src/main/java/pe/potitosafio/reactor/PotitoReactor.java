package pe.potitosafio.reactor;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.*;
import org.bukkit.scoreboard.*;
import org.bukkit.util.RayTraceResult;

import java.util.*;

public final class PotitoReactor extends JavaPlugin implements Listener, CommandExecutor {
    private final Set<String> reactors = new HashSet<>();
    private final Map<UUID, Puzzle> sessions = new HashMap<>();
    private final Map<UUID, Scoreboard> oldBoards = new HashMap<>();
    private final Map<UUID, Scoreboard> eventBoards = new HashMap<>();
    private final Random random = new Random();
    // Una configuración por ronda: los operadores no pueden cambiar el puzzle cerrándolo.
    private int[] cycleOrder = {0, 1, 2, 3, 4};
    private int[] cycleTargets = {0, 1, 2, 3, 4};
    private final Deque<String> recentLayouts = new ArrayDeque<>();
    private static final int LAYOUT_HISTORY = 12;
    private Location active;
    private UUID operator;
    private UUID bossId;
    private boolean running;
    private int time;
    private int mutations;
    private boolean battleEnabled = false;
    private final Set<Integer> battlePhasesTriggered = new HashSet<>();
    private long nextAutoRoundTick = 0;
    private long successDebuffExpires = 0;
    private long shortCircuitExpires = 0;
    private final Map<UUID, Integer> bossMutationLevels = new HashMap<>();
    private int elapsedSeconds;
    private int cycleErrors;
    private int cycleProgress;
    private String status = "ESPERA";
    private final ChatColor[] colors = {ChatColor.RED, ChatColor.BLUE, ChatColor.YELLOW, ChatColor.GREEN, ChatColor.LIGHT_PURPLE};
    private final Material[] glass = {Material.RED_STAINED_GLASS_PANE, Material.BLUE_STAINED_GLASS_PANE,
            Material.YELLOW_STAINED_GLASS_PANE, Material.LIME_STAINED_GLASS_PANE, Material.MAGENTA_STAINED_GLASS_PANE};
    private final int[] left = {10, 19, 28, 37, 46};
    private final int[] right = {16, 25, 34, 43, 52};

    @Override public void onEnable() {
        saveDefaultConfig();
        reactors.addAll(getConfig().getStringList("reactor.locations"));
        for (String entry : getConfig().getStringList("puzzle.recent-layouts")) {
            if (!entry.isBlank() && !recentLayouts.contains(entry)) recentLayouts.addLast(entry);
        }
        while (recentLayouts.size() > LAYOUT_HISTORY) recentLayouts.removeFirst();
        Objects.requireNonNull(getCommand("preactor")).setExecutor(this);
        getServer().getPluginManager().registerEvents(this, this);
        // Un solo recorrido iluminado a la vez: alternamos los cables ya conectados
        // para que sus curvas sean legibles incluso cuando los cinco están completos.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!running) return;
            for (Puzzle puzzle : new ArrayList<>(sessions.values())) {
                if (puzzle.progress > 1) {
                    puzzle.displayedWire++;
                    build(puzzle);
                }
            }
        }, 12L, 12L);
        // Reactor visible: partículas alrededor del bloque físico registrado.
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!running || active == null) return;
            Location core = active.clone().add(0.5, 1.25, 0.5);
            World w = core.getWorld();
            if (w == null) return;
            w.spawnParticle(time <= 10 ? Particle.FLAME : Particle.END_ROD, core,
                    time <= 10 ? 9 : 3, 0.35, 0.35, 0.35, 0.01);
            if (time <= 5) w.spawnParticle(Particle.SMOKE, core, 5, 0.30, 0.5, 0.30, 0.01);
        }, 10L, 10L);
        getServer().getScheduler().runTaskTimer(this, this::checkBossPhase, 20L, 20L);
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!running) return;
            elapsedSeconds++;
            time--;
            if (time <= 0) fail(false, null);
            else {
                if (time == 10) announce("&6ALERTA: &eQuedan 10 segundos para estabilizar el núcleo.");
                if (time == 5) announce("&cFUSIÓN INMINENTE: &f5 segundos para la detonación.");
                if (time <= 10 && time > 0) beep();
                refreshBoards();
            }
        }, 20L, 20L);
        getLogger().info("PotitoReactor v2.1 activo. Compatible por API Bukkit/Paper 1.21.1; probar en Arclight.");
    }

    @Override public void onDisable() {
        running = false;
        closeAll();
        restoreBoards();
    }

    private String key(Location l) {
        return l.getWorld().getName() + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }
    private Location fromKey(String key) {
        try {
            String[] p = key.split(":");
            World w = Bukkit.getWorld(p[0]);
            return w == null ? null : new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception ex) { return null; }
    }
    private void persist() {
        getConfig().set("reactor.locations", new ArrayList<>(reactors));
        saveConfig();
    }
    private String c(String raw) { return ChatColor.translateAlternateColorCodes('&', raw); }
    private void announce(String s) { Bukkit.broadcastMessage(c("&8[&a☢ &2Reactor&8] &r" + s)); }

    @Override public boolean onCommand(CommandSender s, Command command, String label, String[] args) {
        if (!s.hasPermission("potitoreactor.admin")) { s.sendMessage(c("&cSin permisos.")); return true; }
        if (args.length == 0) { s.sendMessage(c("&e/preactor crear, quitar, lista, iniciar, detener, boss, batalla iniciar|detener|estado")); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("lista")) { s.sendMessage(c("&eReactores registrados: " + reactors.size())); reactors.forEach(s::sendMessage); return true; }
        if (action.equals("estado")) { s.sendMessage(c("&eReactor: " + running + " &7| Tiempo: " + time + " | Operador: " + operator + " | Mutaciones: " + mutations + " | Batalla automática: " + battleEnabled + " | Fases: " + battlePhasesTriggered)); return true; }
        if (action.equals("batalla")) {
            String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "estado";
            if (sub.equals("detener")) { battleEnabled = false; s.sendMessage(c("&eActivación automática desactivada. El reactor en curso no se cancela.")); return true; }
            if (sub.equals("estado")) { s.sendMessage(c("&eBatalla: " + battleEnabled + " &7| Fases usadas: " + battlePhasesTriggered + " | Jefe: " + (boss() != null ? boss().getType() : "sin vínculo"))); return true; }
            if (sub.equals("iniciar")) {
                if (battleEnabled) { s.sendMessage(c("&eLa batalla ya está monitoreándose.")); return true; }
                LivingEntity targetBoss = boss();
                if (targetBoss == null) { s.sendMessage(c("&cPrimero vincula al jefe con /preactor boss.")); return true; }
                if (reactors.stream().noneMatch(k -> { Location l = fromKey(k); return l != null && l.getWorld().equals(targetBoss.getWorld()); })) {
                    s.sendMessage(c("&cNo hay reactores registrados en el mundo del jefe.")); return true;
                }
                battlePhasesTriggered.clear();
                nextAutoRoundTick = Bukkit.getCurrentTick() + 40L;
                battleEnabled = true;
                announce("&cPROTOCOLO EXTINCIÓN: &fmonitoreo de Tremorzilla activado. &eReactores al 60% y 25% de vida.");
                return true;
            }
            s.sendMessage(c("&cUsa /preactor batalla iniciar|detener|estado")); return true;
        }
        if (action.equals("detener")) { battleEnabled = false; stop(); s.sendMessage(c("&aEvento y monitoreo automático detenidos.")); return true; }
        if (!(s instanceof Player p)) { s.sendMessage("Este comando requiere un jugador."); return true; }
        if (action.equals("boss")) {
            RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 60.0,
                    2.0, e -> e instanceof LivingEntity && !e.getUniqueId().equals(p.getUniqueId()));
            if (hit == null || !(hit.getHitEntity() instanceof LivingEntity entity)) {
                p.sendMessage(c("&cMira directamente al Tremorzilla dentro de 60 bloques.")); return true;
            }
            if (running) { p.sendMessage(c("&cTermina la ronda antes de cambiar de jefe.")); return true; }
            bossId = entity.getUniqueId();
            p.sendMessage(c("&eComprueba que el tipo y UUID indicados correspondan al Tremorzilla real."));
            battleEnabled = false;
            battlePhasesTriggered.clear();
            successDebuffExpires = 0;
            shortCircuitExpires = 0;
            mutations = bossMutationLevels.getOrDefault(bossId, 0);
            p.sendMessage(c("&aJefe vinculado: &f" + entity.getType() + " &7(" + bossId + ")"));
            return true;
        }
        Block target = p.getTargetBlockExact(6);
        if (action.equals("crear") || action.equals("quitar")) {
            if (target == null || target.getType().isAir()) { p.sendMessage(c("&cMira un bloque a menos de 6 bloques.")); return true; }
            String k = key(target.getLocation());
            boolean changed = action.equals("crear") ? reactors.add(k) : reactors.remove(k);
            persist();
            p.sendMessage(c(changed ? "&aReactor actualizado: &f" + k : "&eNo hubo cambios: " + k));
            return true;
        }
        if (action.equals("iniciar")) {
            if (running) { p.sendMessage(c("&cYa hay un protocolo activo.")); return true; }
            Location selected = null;
            if (target != null && reactors.contains(key(target.getLocation()))) selected = target.getLocation();
            if (selected == null) {
                for (String k : reactors) {
                    Location l = fromKey(k);
                    if (l != null && l.getWorld().equals(p.getWorld()) && l.distanceSquared(p.getLocation()) <= 100) { selected = l; break; }
                }
            }
            if (selected == null) { p.sendMessage(c("&cMira un reactor registrado o acércate a menos de 10 bloques.")); return true; }
            start(selected);
            return true;
        }
        p.sendMessage(c("&cAcción desconocida."));
        return true;
    }

    // El monitoreo no cambia invulnerabilidad ni el objeto que los jugadores destruyen.
    // Se limita a abrir rondas de puzzle en umbrales de salud del jefe vinculado.
    private void checkBossPhase() {
        if (!battleEnabled || running || Bukkit.getCurrentTick() < nextAutoRoundTick) return;
        LivingEntity b = boss();
        if (b == null || b.isDead()) {
            battleEnabled = false;
            if (b == null) announce("&7El jefe ya no está disponible. Se desactivó el monitoreo nuclear.");
            return;
        }
        double max = b.getMaxHealth();
        if (max <= 0) return;
        double percent = b.getHealth() / max * 100.0;
        int phase = -1;
        // Una ronda pendiente de 60% se dispara antes que la de 25%, incluso si baja de golpe.
        if (percent <= getConfig().getDouble("battle.phase-one-percent", 60.0) && !battlePhasesTriggered.contains(1)) phase = 1;
        else if (percent <= getConfig().getDouble("battle.phase-two-percent", 25.0) && !battlePhasesTriggered.contains(2)) phase = 2;
        if (phase < 0) return;
        Location nearest = null;
        double distance = Double.MAX_VALUE;
        for (String k : reactors) {
            Location loc = fromKey(k);
            if (loc == null || !loc.getWorld().equals(b.getWorld())) continue;
            double d = loc.distanceSquared(b.getLocation());
            if (d < distance) { distance = d; nearest = loc; }
        }
        if (nearest == null) { getLogger().warning("Sin reactor para la fase " + phase); return; }
        battlePhasesTriggered.add(phase);
        announce("&4☢ FASE " + phase + ": &cTremorzilla está sobrecargando el núcleo. &f¡Reparen el reactor!");
        start(nearest);
    }

    private void start(Location l) {
        closeAll(); restoreBoards();
        active = l.clone();
        running = true;
        operator = null;
        mutations = bossId == null ? 0 : bossMutationLevels.getOrDefault(bossId, 0);
        elapsedSeconds = 0;
        cycleErrors = 0;
        cycleProgress = 0;
        time = getConfig().getInt("puzzle.seconds", 30);
        createRoundLayout();
        status = "INESTABLE";
        announce("&ePROTOCOLO DE CONTENCIÓN ACTIVADO. &f¡Estabilicen el reactor!");
        refreshBoards();
    }
    // Baraja los extremos de modo independiente, descarta los últimos 12 patrones
    // y evita una solución demasiado evidente con todos los pares enfrentados.
    private void createRoundLayout() {
        String signature;
        int attempts = 0;
        do {
            cycleOrder = new int[] {0, 1, 2, 3, 4};
            cycleTargets = new int[] {0, 1, 2, 3, 4};
            shuffle(cycleOrder);
            shuffle(cycleTargets);
            signature = Arrays.toString(cycleOrder) + ":" + Arrays.toString(cycleTargets);
            attempts++;
        } while (attempts < 10000 && (recentLayouts.contains(signature) || alignedPairs() > 2));
        recentLayouts.addLast(signature);
        while (recentLayouts.size() > LAYOUT_HISTORY) recentLayouts.removeFirst();
        getConfig().set("puzzle.recent-layouts", new ArrayList<>(recentLayouts));
        saveConfig();
    }

    private int alignedPairs() {
        int count = 0;
        for (int i = 0; i < 5; i++) if (cycleOrder[i] == cycleTargets[i]) count++;
        return count;
    }

    private void shuffle(int[] a) {
        for (int i = a.length - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            int tmp = a[i]; a[i] = a[j]; a[j] = tmp;
        }
    }

    private void stop() {
        running = false;
        operator = null;
        status = "ESPERA";
        closeAll();
        restoreBoards();
        active = null;
    }
    private boolean nearby(Player p) {
        double r = getConfig().getDouble("reactor.player-radius", 65.0);
        return active != null && p.getWorld().equals(active.getWorld()) && p.getLocation().distanceSquared(active) <= r*r;
    }
    @EventHandler(ignoreCancelled = true) public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        if (!reactors.contains(key(e.getClickedBlock().getLocation()))) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!running || active == null || !key(active).equals(key(e.getClickedBlock().getLocation()))) {
            p.sendMessage(c("&eEste reactor está inactivo. El administrador debe usar /preactor iniciar.")); return;
        }
        if (operator != null && !operator.equals(p.getUniqueId())) {
            Player op = Bukkit.getPlayer(operator);
            p.sendMessage(c("&cTERMINAL OCUPADO. &7Operador: &f" + (op != null ? op.getName() : "desconocido")));
            return;
        }
        if (sessions.containsKey(p.getUniqueId())) return;
        openPuzzle(p);
    }
    private void openPuzzle(Player p) {
        operator = p.getUniqueId();
        Puzzle puzzle = new Puzzle(p);
        sessions.put(p.getUniqueId(), puzzle);
        build(puzzle);
        p.openInventory(puzzle.inventory);
        status = "OPERANDO";
        announce("&b" + p.getName() + " &fha iniciado la estabilización del reactor. &e¡Protejan al operador!");
        refreshBoards();
    }
    private ItemStack item(Material mat, String name, String... lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(c(name));
        if (lore.length > 0) m.setLore(Arrays.stream(lore).map(this::c).toList());
        it.setItemMeta(m);
        return it;
    }
    // Los iconos usan CustomModelData sobre PAPER, sin reemplazar items normales.
    private ItemStack icon(int model, String name, String... lore) {
        ItemStack it = item(Material.PAPER, name, lore);
        ItemMeta meta = it.getItemMeta();
        meta.setCustomModelData(model);
        it.setItemMeta(meta);
        return it;
    }

    private void build(Puzzle p) {
        Inventory inv = p.inventory;
        ItemStack filler = icon(72100, "&8 ");
        for (int i = 0; i < 54; i++) inv.setItem(i, filler);
        for (int i = 0; i < 5; i++) {
            int color = p.order[i];
            boolean done = p.done[color];
            inv.setItem(left[i], icon(done ? 72112 : (p.selected == color ? 72106 + color : 72101 + color),
                    done ? "&a✔ Circuito conectado" : colors[color] + "Cable " + (i + 1),
                    done ? "&aCompletado" : "&7Haz clic para seleccionar este cable"));
        }
        for (int i = 0; i < 5; i++) {
            int color = p.targets[i];
            boolean done = p.done[color];
            inv.setItem(right[i], icon(done ? 72112 : 72101 + color,
                    done ? "&a✔ Terminal conectado" : colors[color] + "Terminal " + (i + 1),
                    done ? "&aCompletado" : "&7Selecciona un cable y después este terminal"));
        }
        inv.setItem(4, icon(72113, "&eTiempo: &f" + time + " s"));
        // El centro (columnas 2..6, filas 1..5) queda libre para los recorridos.
        inv.setItem(48, icon(72114, "&cErrores: &f" + p.errors + "/" + getConfig().getInt("puzzle.wrong-attempts", 3)));
        inv.setItem(50, icon(72115, "&bCircuitos: &f" + p.progress + "/5"));
        inv.setItem(49, icon(72116, "&cSalir", "&7El avance se perderá; el tiempo sigue."));
        if (p.selected >= 0) inv.setItem(0, icon(72117, "&eCable seleccionado: &f" + (p.selected + 1)));
        else inv.setItem(0, icon(72118, "&7Selecciona un cable izquierdo"));
        drawWiring(p);
    }
    // V1.3: ruta ortogonal visual por el panel central. Los extremos permanecen
    // en las mismas casillas de siempre; las curvas del medio son sólo visuales.
    // Los cinco circuitos completados se muestran alternándose, evitando que
    // sus texturas se oculten mutuamente en intersecciones del inventario.
    // IDs: 72200 + color*10 + tramo (0=H,1=V,2=L->D,3=L->U,4=U->R,5=D->R).
    private void drawWiring(Puzzle p) {
        if (p.progress == 0) return;
        int color = -1;
        int remaining = Math.floorMod(p.displayedWire, p.progress);
        for (int c = 0; c < 5; c++) {
            if (!p.done[c]) continue;
            if (remaining-- == 0) { color = c; break; }
        }
        if (color < 0) return;
        int start = indexOf(p.order, color) + 1;
        int end = indexOf(p.targets, color) + 1;
        if (start == 0 || end == 0) return;
        // Las columnas 2 y 3 salen del conector izquierdo (columna 1).
        for (int col = 2; col <= 3; col++) wireTile(p, start, col, color, 0);
        // Las columnas 5 y 6 llegan al conector derecho (columna 7).
        for (int col = 5; col <= 6; col++) wireTile(p, end, col, color, 0);
        if (start == end) wireTile(p, start, 4, color, 0);
        else {
            wireTile(p, start, 4, color, start < end ? 2 : 3);
            wireTile(p, end, 4, color, start < end ? 4 : 5);
            for (int row = Math.min(start, end) + 1; row < Math.max(start, end); row++)
                wireTile(p, row, 4, color, 1);
        }
    }
    private int indexOf(int[] array, int value) {
        for (int i = 0; i < array.length; i++) if (array[i] == value) return i;
        return -1;
    }
    private void wireTile(Puzzle p, int row, int col, int color, int segment) {
        p.inventory.setItem(row * 9 + col,
            icon(72200 + color * 10 + segment,
                colors[color] + "Circuito " + (color + 1) + " &8| &aConectado",
                "&7Ruta luminosa del cable completado"));
    }
    @EventHandler public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;
        Puzzle p = sessions.get(player.getUniqueId());
        if (p == null || !e.getView().getTopInventory().equals(p.inventory)) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= 54) return;
        if (slot == 49) { player.closeInventory(); return; }
        for (int i=0; i<5; i++) if (slot == left[i]) {
            if (!p.done[p.order[i]]) p.selected = p.order[i];
            build(p); return;
        }
        for (int i=0; i<5; i++) if (slot == right[i]) {
            int color = p.targets[i];
            if (p.selected < 0 || p.done[color]) return;
            if (p.selected == color) {
                p.done[color] = true;
                p.progress++;
                p.displayedWire = 0;
                for (int preceding = 0; preceding < color; preceding++)
                    if (p.done[preceding]) p.displayedWire++;
                cycleProgress = p.progress;
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.4f);
                p.selected = -1;
                if (p.progress == 5) { success(player); return; }
            } else {
                p.errors++;
                cycleErrors = p.errors;
                p.selected = -1;
                announce("&cERROR DE CONEXIÓN: &f" + player.getName() + " &7ha conectado un cable incorrecto. &c" + p.errors + "/" + getConfig().getInt("puzzle.wrong-attempts",3));
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.5f);
                if (p.errors >= getConfig().getInt("puzzle.wrong-attempts",3)) { fail(true, player); return; }
            }
            build(p); refreshBoards(); return;
        }
    }
    @EventHandler public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Puzzle s = sessions.get(p.getUniqueId());
        if (s != null && e.getView().getTopInventory().equals(s.inventory)) e.setCancelled(true);
    }
    @EventHandler public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Puzzle puzzle = sessions.get(p.getUniqueId());
        if (puzzle == null || !e.getInventory().equals(puzzle.inventory)) return;
        sessions.remove(p.getUniqueId());
        if (operator != null && operator.equals(p.getUniqueId()) && running) {
            operator = null;
            cycleErrors = 0;
            cycleProgress = 0;
            status = "SIN OPERADOR";
            announce("&e" + p.getName() + " &7abandonó el terminal. La secuencia debe reiniciarse.");
            refreshBoards();
        }
    }
    // En Arclight otros mods/plugins pueden ajustar el daño después de nuestros listeners.
    // MONITOR lee el daño final y el siguiente tick comprueba la pérdida real de vida/absorción.
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !running) return;
        Puzzle puzzle = sessions.get(p.getUniqueId());
        if (puzzle == null) return;
        double finalDamage = Math.max(0, e.getFinalDamage());
        if (finalDamage <= 0) return;
        puzzle.pendingDamage += finalDamage;
        if (puzzle.damageCheckScheduled) return;
        puzzle.damageCheckScheduled = true;
        final double healthBefore = p.getHealth();
        final double absorptionBefore = p.getAbsorptionAmount();
        Bukkit.getScheduler().runTask(this, () -> {
            puzzle.damageCheckScheduled = false;
            if (!running || sessions.get(p.getUniqueId()) != puzzle) return;
            double lost = Math.max(0, healthBefore - p.getHealth()) +
                    Math.max(0, absorptionBefore - p.getAbsorptionAmount());
            // No sumar dos veces el mismo golpe: usar la mayor medición.
            double received = Math.max(puzzle.pendingDamage, lost);
            puzzle.pendingDamage = 0;
            puzzle.damage += received;
            double threshold = getConfig().getDouble("puzzle.cancel-damage", 4.0);
            if (puzzle.damage + 1.0E-6 >= threshold) {
                evacuate(p, puzzle);
            } else if (getConfig().getBoolean("puzzle.show-damage-debug", true)) {
                p.sendMessage(c(String.format(java.util.Locale.ROOT,
                    "&8[&a☢ &2Reactor&8] &cDaño acumulado: %.1f / %.1f HP", puzzle.damage, threshold)));
            }
        });
    }
    private void evacuate(Player p, Puzzle puzzle) {
        if (sessions.get(p.getUniqueId()) != puzzle) return;
        sessions.remove(p.getUniqueId());
        if (p.getUniqueId().equals(operator)) operator = null;
        cycleErrors = 0;
        cycleProgress = 0;
        status = "INTERRUMPIDO";
        p.closeInventory();
        announce("&6OPERADOR COMPROMETIDO: &f" + p.getName()
                + " &eha sido evacuado del sistema de contención. &c¡Reinicien la secuencia!");
        refreshBoards();
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        UUID id=e.getPlayer().getUniqueId();
        if (sessions.remove(id) != null && id.equals(operator)) {
            operator=null; cycleErrors=0; cycleProgress=0; status="SIN OPERADOR"; refreshBoards();
        }
        oldBoards.remove(id); eventBoards.remove(id);
    }
    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent e) {
        if (reactors.contains(key(e.getBlock().getLocation()))) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(c("&cReactor protegido: usa /preactor quitar para desregistrarlo."));
        }
    }
    private void closeAll() {
        for (Puzzle p : new ArrayList<>(sessions.values())) {
            sessions.remove(p.player);
            Player player = Bukkit.getPlayer(p.player);
            if (player != null && player.isOnline()) player.closeInventory();
        }
    }
    private void success(Player player) {
        announce("&aESTABILIZACIÓN COMPLETADA: &f" + player.getName() + " &aha restablecido los cinco circuitos.");
        playSuccessEffects();
        LivingEntity boss = boss();
        if (boss != null) boss.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                getConfig().getInt("success.weakness-seconds",15)*20, 0));
        successDebuffExpires = Bukkit.getCurrentTick() + 20L * Math.max(0, getConfig().getInt("battle.success-weaken-seconds", 15));
        status="ESTABILIZADO";
        stop();
        nextAutoRoundTick = Bukkit.getCurrentTick() + 20L * Math.max(1, getConfig().getInt("battle.round-cooldown-seconds", 12));
    }
    private void playSuccessEffects() {
        if (active == null) return;
        Location center = active.clone().add(0.5, 1.0, 0.5);
        center.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center, 35, 1, 1, 1, 0);
        center.getWorld().playSound(center, Sound.BLOCK_BEACON_ACTIVATE, 1.5f, 1.4f);
    }

    private void fail(boolean mistakes, Player culprit) {
        if (!running || active == null) return;
        status = "DETONADO";
        if (mistakes) announce("&cCORTOCIRCUITO NUCLEAR: &f" + (culprit == null ? "Un operador" : culprit.getName())
                + " &cprovocó el fallo crítico.");
        else announce("&4FUSIÓN DEL NÚCLEO: &cEl tiempo de estabilización ha expirado.");

        Location blastCenter = active.clone().add(0.5, 1.0, 0.5);
        List<Player> participants = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (nearby(p) && p.getGameMode() == GameMode.SURVIVAL) participants.add(p);
        }
        // Importante: cerrar ANTES de afectar jugadores; esto no debe disparar evacuaciones.
        running = false;
        operator = null;
        closeAll();

        blastCenter.getWorld().spawnParticle(Particle.EXPLOSION, blastCenter, 7, 2.0, 1.4, 2.0, 0);
        blastCenter.getWorld().spawnParticle(Particle.FLAME, blastCenter, 100, 3.0, 2.0, 3.0, 0.06);
        blastCenter.getWorld().playSound(blastCenter, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        // Sin World#createExplosion: nunca se rompen bloques por la detonación del plugin.
        for (Player p : participants) applyNuclearBlast(p);

        LivingEntity b = boss();
        if (b != null) {
            if (mistakes) {
                b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                        20 * Math.max(1, getConfig().getInt("failure.strength-seconds", 45)), 0));
            } else {
                mutations = Math.min(Math.max(1, getConfig().getInt("failure.max-mutations", 3)), mutations + 1);
                bossMutationLevels.put(b.getUniqueId(), mutations);
                int secs = Math.max(1, getConfig().getInt("failure.mutation-seconds", 3600));
                b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, secs * 20, mutations >= 3 ? 1 : 0));
                if (mutations >= 2)
                    b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs * 20, 0));
                double healed = b.getHealth() + b.getMaxHealth() * getConfig().getDouble("failure.heal-boss-fraction", 0.10);
                b.setHealth(Math.min(b.getMaxHealth(), healed));
            }
        } else announce("&eNo hay jefe vinculado: se aplicó la detonación, pero no una mejora a Tremorzilla.");

        if (b != null && mistakes) shortCircuitExpires = Bukkit.getCurrentTick() + 20L * Math.max(1, getConfig().getInt("failure.strength-seconds", 45));
        if (mistakes) announce("&6DESCARGA NUCLEAR: &cTremorzilla ha recibido Fuerza I temporal.");
        else announce("&4MUTACIÓN NUCLEAR " + mutations + ": &cTremorzilla absorbió la energía del reactor.");
        restoreBoards();
        active = null;
        nextAutoRoundTick = Bukkit.getCurrentTick() + 20L * Math.max(1, getConfig().getInt("battle.round-cooldown-seconds", 12));
    }

    // Refuerzo fiable de golpes directos: no depende de que el mod respete Fuerza.
    // Proyectiles personalizados de Alex's Caves pueden no exponer su dueño en Bukkit;
    // esos ataques quedan fuera y se verifican por separado.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBossAttack(EntityDamageByEntityEvent event) {
        LivingEntity b = boss();
        if (b == null || !(event.getEntity() instanceof Player victim) || !nearBattleBoss(victim, b)) return;
        Entity damager = event.getDamager();
        boolean direct = damager.getUniqueId().equals(b.getUniqueId());
        boolean projectile = damager instanceof Projectile pr && pr.getShooter() instanceof Entity shooter
                && shooter.getUniqueId().equals(b.getUniqueId());
        if (!direct && !projectile) return;
        double multiplier = 1.0 + mutations * getConfig().getDouble("battle.damage-per-mutation", 0.12);
        if (Bukkit.getCurrentTick() < shortCircuitExpires) multiplier += getConfig().getDouble("battle.short-circuit-damage-bonus", 0.20);
        if (Bukkit.getCurrentTick() < successDebuffExpires) multiplier *= getConfig().getDouble("battle.success-damage-multiplier", 0.75);
        if (multiplier != 1.0) event.setDamage(Math.max(0, event.getDamage() * multiplier));
    }
    private boolean nearBattleBoss(Player victim, LivingEntity b) {
        return victim.getWorld().equals(b.getWorld()) && victim.getLocation().distanceSquared(b.getLocation()) <= 10000;
    }

    /**
     * Nunca usar daño letal de 1000 HP para consumir tótems: en servidores híbridos
     * puede atravesar defensas y matar a los participantes. Consumimos exactamente
     * una unidad en la mano equipada y mostramos animación + efectos de rescate.
     * No simula un evento de resurrección vanilla; es una penalización personalizada.
     */
    private void applyNuclearBlast(Player p) {
        if (getConfig().getBoolean("failure.consume-totem", true)) {
            boolean off = p.getInventory().getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING;
            boolean main = p.getInventory().getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING;
            if (off || main) {
                EquipmentSlot hand = off ? EquipmentSlot.OFF_HAND : EquipmentSlot.HAND;
                ItemStack held = hand == EquipmentSlot.OFF_HAND
                        ? p.getInventory().getItemInOffHand() : p.getInventory().getItemInMainHand();
                ItemStack remaining = held.clone();
                if (held.getAmount() > 1) remaining.setAmount(held.getAmount() - 1);
                if (hand == EquipmentSlot.OFF_HAND) p.getInventory().setItemInOffHand(held.getAmount() <= 1 ? null : remaining);
                else p.getInventory().setItemInMainHand(held.getAmount() <= 1 ? null : remaining);
                p.playEffect(EntityEffect.TOTEM_RESURRECT);
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 20 * 25, 1));
                p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 20 * 5, 1));
                p.sendMessage(c("&c☢ La onda nuclear consumió uno de tus tótems."));
                return;
            }
        }
        // Quien no tenga tótem recibe el daño configurado; puede ser mortal.
        p.damage(Math.max(0, getConfig().getDouble("failure.damage-without-totem", 14.0)));
        p.sendMessage(c("&c☢ La onda nuclear te alcanzó sin protección de un tótem."));
    }

    private LivingEntity boss() {
        if (bossId==null) return null;
        Entity e=Bukkit.getEntity(bossId);
        return e instanceof LivingEntity living && e.isValid() ? living : null;
    }
    private void beep() {
        if (active == null) return;
        for (Player p : Bukkit.getOnlinePlayers()) if (nearby(p))
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.65f, 0.7f);
    }
    private void refreshBoards() {
        if (!running) return;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!nearby(p)) continue;
            UUID id=p.getUniqueId();
            Scoreboard board=eventBoards.get(id);
            if (board==null) {
                oldBoards.put(id,p.getScoreboard());
                board=Objects.requireNonNull(Bukkit.getScoreboardManager()).getNewScoreboard();
                eventBoards.put(id,board);
                p.setScoreboard(board);
            }
            Objective objective=board.getObjective("reactor");
            if (objective!=null) objective.unregister();
            objective=board.registerNewObjective("reactor", Criteria.DUMMY, c("&a☢ &2PROTOCOLO EXTINCIÓN"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
            String who="Nadie";
            Player op=operator==null ? null : Bukkit.getPlayer(operator);
            if (op != null) who=op.getName();
            String[] lines={
                    "&8&m-------------", "&fOperador: &b"+who,
                    "&fTiempo: &e"+String.format("%02d:%02d",time/60,time%60),
                    "&fCables: &b"+cycleProgress+"/5", "&fErrores: &c"+cycleErrors+"/"+getConfig().getInt("puzzle.wrong-attempts",3),
                    "&fEstado: &6"+status, "&7Protege al operador", "&8&m--------------"
            };
            for (int i=0;i<lines.length;i++) objective.getScore(c(lines[i])).setScore(lines.length-i);
        }
    }
    private void restoreBoards() {
        for (Map.Entry<UUID,Scoreboard> ent:oldBoards.entrySet()) {
            Player p=Bukkit.getPlayer(ent.getKey());
            if (p!=null && p.isOnline() && p.getScoreboard()==eventBoards.get(ent.getKey())) p.setScoreboard(ent.getValue());
        }
        oldBoards.clear(); eventBoards.clear();
    }
    private final class Puzzle {
        final UUID player;
        final Inventory inventory;
        final int[] order;
        final int[] targets;
        final boolean[] done=new boolean[5];
        int selected=-1, errors=0, progress=0, displayedWire=0;
        double damage=0;
        double pendingDamage=0;
        boolean damageCheckScheduled=false;
        Puzzle(Player p) {
            player=p.getUniqueId();
            inventory=Bukkit.createInventory(null,54,c("&2☢ &0REACTOR: RECONEXIÓN"));
            order = cycleOrder.clone();
            targets = cycleTargets.clone();
        }
    }
}
