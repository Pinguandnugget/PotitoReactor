package pe.potitosafio.reactor;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
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
        getServer().getScheduler().runTaskTimer(this, () -> {
            if (!running) return;
            time--;
            if (time <= 0) fail(false, null);
            else { if (time <= 10 && time > 0) beep(); refreshBoards(); }
        }, 20L, 20L);
        getLogger().info("PotitoReactor v1.1 activo. Compatible por API Bukkit/Paper 1.21.1; probar en Arclight.");
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
        if (args.length == 0) { s.sendMessage(c("&e/preactor crear, quitar, lista, iniciar, detener, boss, estado")); return true; }
        String action = args[0].toLowerCase(Locale.ROOT);
        if (action.equals("lista")) { s.sendMessage(c("&eReactores registrados: " + reactors.size())); reactors.forEach(s::sendMessage); return true; }
        if (action.equals("estado")) { s.sendMessage(c("&eEvento: " + running + " &7| Tiempo: " + time + " | Operador: " + operator + " | Mutaciones: " + mutations)); return true; }
        if (action.equals("detener")) { stop(); s.sendMessage(c("&aEvento detenido.")); return true; }
        if (!(s instanceof Player p)) { s.sendMessage("Este comando requiere un jugador."); return true; }
        if (action.equals("boss")) {
            RayTraceResult hit = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 60.0,
                    2.0, e -> e instanceof LivingEntity && !e.getUniqueId().equals(p.getUniqueId()));
            if (hit == null || !(hit.getHitEntity() instanceof LivingEntity entity)) {
                p.sendMessage(c("&cMira directamente al Tremorzilla dentro de 60 bloques.")); return true;
            }
            bossId = entity.getUniqueId();
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

    private void start(Location l) {
        closeAll(); restoreBoards();
        active = l.clone();
        running = true;
        operator = null;
        mutations = 0;
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
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
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
        inv.setItem(22, icon(72114, "&cErrores: &f" + p.errors + "/" + getConfig().getInt("puzzle.wrong-attempts", 3)));
        inv.setItem(31, icon(72115, "&bCircuitos: &f" + p.progress + "/5"));
        inv.setItem(49, icon(72116, "&cSalir", "&7El avance se perderá; el tiempo sigue."));
        if (p.selected >= 0) inv.setItem(13, icon(72117, "&eCable seleccionado: &f" + (p.selected + 1)));
        else inv.setItem(13, icon(72118, "&7Selecciona un cable izquierdo"));
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
        LivingEntity boss = boss();
        if (boss != null) boss.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS,
                getConfig().getInt("success.weakness-seconds",15)*20, 0));
        status="ESTABILIZADO";
        stop();
    }
    private void fail(boolean mistakes, Player culprit) {
        if (!running) return;
        status = "DETONADO";
        if (mistakes) announce("&cCORTOCIRCUITO NUCLEAR: &f" + (culprit == null ? "Un operador" : culprit.getName()) + " &cprovocó el fallo crítico.");
        else announce("&4FUSIÓN DEL NÚCLEO: &cEl tiempo de estabilización ha expirado.");
        Location center = active.clone().add(0.5,1,0.5);
        center.getWorld().spawnParticle(Particle.EXPLOSION, center, 5, 1.5,1.5,1.5, 0);
        center.getWorld().playSound(center, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.6f);
        LivingEntity b=boss();
        if (b != null) {
            if (mistakes) b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH,
                    getConfig().getInt("failure.strength-seconds",45)*20, 0));
            else {
                mutations = Math.min(3, mutations + 1);
                int secs=getConfig().getInt("failure.mutation-seconds",3600);
                b.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, secs*20, mutations>=3 ? 1 : 0));
                if (mutations>=2) b.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, secs*20, 0));
                double healed=b.getHealth()+b.getMaxHealth()*getConfig().getDouble("failure.heal-boss-fraction", 0.10);
                b.setHealth(Math.min(b.getMaxHealth(), healed));
            }
        }
        // Detener antes del daño: no convertir el daño de la detonación en una evacuación adicional.
        running=false;
        operator=null;
        closeAll();
        for (Player p : Bukkit.getOnlinePlayers()) if (nearby(p) && p.getGameMode()!=GameMode.CREATIVE && p.getGameMode()!=GameMode.SPECTATOR) {
            boolean totem=(p.getInventory().getItemInMainHand().getType()==Material.TOTEM_OF_UNDYING ||
                    p.getInventory().getItemInOffHand().getType()==Material.TOTEM_OF_UNDYING);
            if (totem && getConfig().getBoolean("failure.consume-totem",true)) {
                // Daño letal convencional: Minecraft activa el tótem si lo tiene equipado.
                p.damage(1000.0);
            } else p.damage(getConfig().getDouble("failure.damage-without-totem",14.0));
        }
        announce(mistakes ? "&6Descarga absorbida: &cTremorzilla obtuvo Fuerza I temporal." :
                "&4MUTACIÓN NUCLEAR: &cTremorzilla absorbió el núcleo y recuperó energía.");
        restoreBoards();
        active=null;
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
        int selected=-1, errors=0, progress=0;
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
