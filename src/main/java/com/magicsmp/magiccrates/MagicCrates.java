package com.magicsmp.magiccrates;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class MagicCrates extends JavaPlugin implements Listener, CommandExecutor, TabCompleter {
    private final LegacyComponentSerializer colors = LegacyComponentSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
    private final Map<String, Location> locations = new HashMap<>();
    private final Set<UUID> rolling = new HashSet<>();
    private File dataFile, locationsFile;
    private YamlConfiguration data, locationsYaml;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "playerdata.yml");
        locationsFile = new File(getDataFolder(), "locations.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        locationsYaml = YamlConfiguration.loadConfiguration(locationsFile);
        loadLocations();
        getServer().getPluginManager().registerEvents(this, this);
        Objects.requireNonNull(getCommand("keys")).setExecutor(this);
        PluginCommand admin = Objects.requireNonNull(getCommand("magiccrates")); admin.setExecutor(this); admin.setTabCompleter(this);
    }

    private Component c(String s) { return colors.deserialize(s == null ? "" : s); }
    private String crateName(String id) { return getConfig().getString("crates." + id + ".name", id); }
    private Set<String> crateIds() { ConfigurationSection s=getConfig().getConfigurationSection("crates"); return s==null?Set.of():s.getKeys(false); }
    private int keys(UUID id, String crate) { return data.getInt("players."+id+".keys."+crate); }
    private void setKeys(UUID id,String crate,int amount){ data.set("players."+id+".keys."+crate,Math.max(0,amount)); saveData(); }
    private void saveData(){ try { data.save(dataFile); } catch(IOException e){ getLogger().severe("Could not save playerdata.yml: "+e.getMessage()); } }

    private void loadLocations(){ locations.clear(); for(String id:locationsYaml.getKeys(false)){ Location l=locationsYaml.getLocation(id); if(l!=null) locations.put(id,l); } }
    private void saveLocations(){ try{ locationsYaml.save(locationsFile); }catch(IOException e){getLogger().severe("Could not save locations.yml: "+e.getMessage());}}

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if(command.getName().equalsIgnoreCase("keys")){ if(!(sender instanceof Player p)){sender.sendMessage("Players only.");return true;} openKeys(p); return true; }
        if(!sender.hasPermission("magiccrates.admin")){sender.sendMessage(c("&cNo permission."));return true;}
        if(args.length==1 && args[0].equalsIgnoreCase("reload")){ reloadConfig(); data=YamlConfiguration.loadConfiguration(dataFile); locationsYaml=YamlConfiguration.loadConfiguration(locationsFile); loadLocations(); sender.sendMessage(c("&aMagicCrates reloaded.")); return true; }
        if(args.length==2 && args[0].equalsIgnoreCase("set") && sender instanceof Player p){ String id=args[1].toLowerCase(); if(!crateIds().contains(id)){sender.sendMessage(c("&cUnknown crate."));return true;} Location l=p.getLocation().getBlock().getLocation(); locations.put(id,l); locationsYaml.set(id,l); saveLocations(); Material m=Material.matchMaterial(getConfig().getString("crates."+id+".block","CHEST")); if(m!=null) l.getBlock().setType(m); sender.sendMessage(c("&aSet "+crateName(id)+" &acrate here.")); return true; }
        if(args.length==2 && args[0].equalsIgnoreCase("remove")){ String id=args[1].toLowerCase(); locations.remove(id); locationsYaml.set(id,null); saveLocations(); sender.sendMessage(c("&aRemoved crate location.")); return true; }
        if(args.length==6 && args[0].equalsIgnoreCase("keys")){ Player target=Bukkit.getPlayerExact(args[2]); String id=args[3].toLowerCase(); int amount; try{amount=Integer.parseInt(args[4]);}catch(Exception e){amount=-1;} sender.sendMessage(c("&cUse: /magiccrates keys <give|remove|set> <player> <crate> <amount>")); return true; }
        if(args.length==5 && args[0].equalsIgnoreCase("keys")){ String action=args[1].toLowerCase(); Player target=Bukkit.getPlayerExact(args[2]); String id=args[3].toLowerCase(); int amount; try{amount=Integer.parseInt(args[4]);}catch(Exception e){amount=-1;} if(target==null||!crateIds().contains(id)||amount<0){sender.sendMessage(c("&cInvalid player, crate, or amount."));return true;} int old=keys(target.getUniqueId(),id); int value=switch(action){case "give"->old+amount;case "remove"->old-amount;case "set"->amount;default->-1;}; if(value<0){sender.sendMessage(c("&cUnknown action or not enough keys."));return true;} setKeys(target.getUniqueId(),id,value); sender.sendMessage(c("&aUpdated "+target.getName()+"'s "+crateName(id)+" &akey balance to &f"+value)); if(action.equals("give")) target.sendMessage(c(msg("key-received",target,id,null).replace("%amount%",String.valueOf(amount)))); return true; }
        sender.sendMessage(c("&6MagicCrates commands:\n&f/magiccrates set <crate>\n/magiccrates remove <crate>\n/magiccrates keys <give|remove|set> <player> <crate> <amount>\n/magiccrates reload")); return true;
    }

    private String msg(String key, Player p, String crate, String reward){ return getConfig().getString("messages.prefix","")+getConfig().getString("messages."+key,"").replace("%player%",p.getName()).replace("%crate%",crateName(crate)).replace("%reward%",reward==null?"":reward); }
    private ItemStack item(Material mat,String name,List<String> lore){ ItemStack i=new ItemStack(mat); ItemMeta m=i.getItemMeta(); m.displayName(c(name)); if(lore!=null)m.lore(lore.stream().map(this::c).toList()); i.setItemMeta(m); return i; }
    private void openKeys(Player p){ Inventory inv=Bukkit.createInventory(null,27,c("&8Your Digital Crate Keys")); int slot=11; for(String id:crateIds()){ Material mat=Material.matchMaterial(getConfig().getString("crates."+id+".icon","CHEST")); inv.setItem(slot++,item(mat==null?Material.CHEST:mat,crateName(id),List.of("&7Digital keys: &f"+keys(p.getUniqueId(),id),"","&7Use at the matching crate."))); } p.openInventory(inv); }

    @EventHandler public void interact(PlayerInteractEvent e){ if(e.getClickedBlock()==null)return; String id=at(e.getClickedBlock()); if(id==null)return; e.setCancelled(true); if(e.getAction().isLeftClick()) preview(e.getPlayer(),id); else if(e.getAction().isRightClick()) open(e.getPlayer(),id); }
    @EventHandler public void breakBlock(BlockBreakEvent e){ String id=at(e.getBlock()); if(id!=null && !e.getPlayer().hasPermission("magiccrates.admin")){e.setCancelled(true);e.getPlayer().sendMessage(c("&cYou cannot break a crate."));} }
    @EventHandler public void pistonExtend(BlockPistonExtendEvent e){ if(e.getBlocks().stream().anyMatch(b -> at(b) != null)) e.setCancelled(true); }
    @EventHandler public void pistonRetract(BlockPistonRetractEvent e){ if(e.getBlocks().stream().anyMatch(b -> at(b) != null)) e.setCancelled(true); }
    @EventHandler public void entityExplode(EntityExplodeEvent e){ e.blockList().removeIf(b -> at(b) != null); }
    @EventHandler public void blockExplode(BlockExplodeEvent e){ e.blockList().removeIf(b -> at(b) != null); }
    private String at(Block b){ for(var en:locations.entrySet()) if(en.getValue().getWorld()!=null&&en.getValue().getWorld().equals(b.getWorld())&&en.getValue().getBlockX()==b.getX()&&en.getValue().getBlockY()==b.getY()&&en.getValue().getBlockZ()==b.getZ())return en.getKey(); return null; }
    @EventHandler public void click(InventoryClickEvent e){ String title=colors.serialize(e.getView().title()); if(title.contains("Crate")||title.contains("Digital Crate Keys")){e.setCancelled(true);} }

    private List<Reward> rewards(String id){ List<Reward> out=new ArrayList<>(); ConfigurationSection s=getConfig().getConfigurationSection("crates."+id+".rewards"); if(s!=null)for(String k:s.getKeys(false)){ Material m=Material.matchMaterial(s.getString(k+".material","STONE")); if(m!=null)out.add(new Reward(m,s.getInt(k+".amount",1),s.getDouble(k+".weight",1),s.getString(k+".name","&f"+k))); } return out; }
    private void preview(Player p,String id){ Inventory inv=Bukkit.createInventory(null,54,c(crateName(id)+" &8Crate Preview")); Material pane=Material.matchMaterial(getConfig().getString("crates."+id+".color","GRAY_STAINED_GLASS_PANE")); ItemStack glass=item(pane==null?Material.GRAY_STAINED_GLASS_PANE:pane," ",null); for(int n=0;n<54;n++)inv.setItem(n,glass); int slot=10; for(Reward r:rewards(id)){ if(slot>=44)break; if(slot%9==8)slot+=2; inv.setItem(slot++,item(r.material,r.name,List.of("&7Chance weight: &f"+r.weight,"&7Amount: &f"+r.amount))); } p.openInventory(inv); }
    private void open(Player p,String id){ if(rolling.contains(p.getUniqueId()))return; if(keys(p.getUniqueId(),id)<1){p.sendMessage(c(msg("no-key",p,id,null)));p.playSound(p,Sound.ENTITY_VILLAGER_NO,1,1);return;} List<Reward> list=rewards(id); if(list.isEmpty())return; setKeys(p.getUniqueId(),id,keys(p.getUniqueId(),id)-1); rolling.add(p.getUniqueId()); Inventory inv=Bukkit.createInventory(null,27,c(crateName(id)+" &8Crate Opening")); p.openInventory(inv); int total=Math.max(20,getConfig().getInt("settings.animation-ticks",60)), every=Math.max(1,getConfig().getInt("settings.roll-every-ticks",3)); new BukkitRunnable(){int ticks=0; public void run(){ if(!p.isOnline()){rolling.remove(p.getUniqueId());cancel();return;} Reward shown=list.get(ThreadLocalRandom.current().nextInt(list.size())); inv.setItem(13,item(shown.material,shown.name,List.of("&7Rolling..."))); p.playSound(p,Sound.UI_BUTTON_CLICK,.5f,1.2f); ticks+=every; if(ticks>=total){cancel(); Reward won=choose(list); ItemStack prize=item(won.material,won.name,null); prize.setAmount(Math.min(won.amount,prize.getMaxStackSize())); HashMap<Integer,ItemStack> left=p.getInventory().addItem(prize); left.values().forEach(x->p.getWorld().dropItemNaturally(p.getLocation(),x)); p.playSound(p,Sound.UI_TOAST_CHALLENGE_COMPLETE,1,1); p.sendMessage(c(msg("win",p,id,won.name))); if(getConfig().getBoolean("settings.broadcast-wins")) Bukkit.broadcast(c(msg("broadcast",p,id,won.name))); rolling.remove(p.getUniqueId()); Bukkit.getScheduler().runTaskLater(MagicCrates.this,p::closeInventory,30); }} }.runTaskTimer(this,0,every); }
    private Reward choose(List<Reward> list){ double total=list.stream().mapToDouble(r->Math.max(0,r.weight)).sum(), roll=ThreadLocalRandom.current().nextDouble(total), now=0; for(Reward r:list){now+=Math.max(0,r.weight);if(roll<now)return r;}return list.getLast();}
    @Override public List<String> onTabComplete(CommandSender s,Command c,String a,String[] x){ if(x.length==1)return List.of("set","remove","keys","reload"); if(x.length==2&&(x[0].equalsIgnoreCase("set")||x[0].equalsIgnoreCase("remove")))return new ArrayList<>(crateIds()); if(x.length==2&&x[0].equalsIgnoreCase("keys"))return List.of("give","remove","set"); if(x.length==3&&x[0].equalsIgnoreCase("keys"))return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList(); if(x.length==4&&x[0].equalsIgnoreCase("keys"))return new ArrayList<>(crateIds()); return List.of(); }
    private record Reward(Material material,int amount,double weight,String name){}
}
