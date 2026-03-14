/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  org.apache.ignite.Ignite
 *  org.apache.ignite.IgniteCache
 *  org.apache.ignite.Ignition
 *  org.apache.ignite.configuration.CacheConfiguration
 *  org.apache.ignite.configuration.IgniteConfiguration
 *  org.apache.ignite.spi.discovery.DiscoverySpi
 *  org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi
 *  org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder
 *  org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder
 */
package services.sandbox.ignite;

import java.util.List;
import org.apache.ignite.Ignite;
import org.apache.ignite.IgniteCache;
import org.apache.ignite.Ignition;
import org.apache.ignite.configuration.CacheConfiguration;
import org.apache.ignite.configuration.IgniteConfiguration;
import org.apache.ignite.spi.discovery.DiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.TcpDiscoverySpi;
import org.apache.ignite.spi.discovery.tcp.ipfinder.TcpDiscoveryIpFinder;
import org.apache.ignite.spi.discovery.tcp.ipfinder.vm.TcpDiscoveryVmIpFinder;
import services.sandbox.model.Position;
import services.sandbox.model.SandboxUser;
import services.sandbox.model.TradeRecord;
import utils.ConfigLoader;

public class SandboxIgniteManager {
    private final Ignite ignite;
    private final IgniteCache<String, SandboxUser> usersCache;
    private final IgniteCache<String, Position> positionsCache;
    private final IgniteCache<String, TradeRecord> tradesCache;

    public SandboxIgniteManager() {
        IgniteConfiguration cfg = new IgniteConfiguration();
        cfg.setIgniteInstanceName("stonks-sandbox-client");
        cfg.setClientMode(true);
        cfg.setWorkDirectory(ConfigLoader.getIgniteWorkDir());
        TcpDiscoverySpi spi = new TcpDiscoverySpi();
        spi.setLocalAddress(ConfigLoader.getIgniteLocalAddress());
        TcpDiscoveryVmIpFinder ipFinder = new TcpDiscoveryVmIpFinder();
        List<String> addrs = ConfigLoader.getIgniteDiscoveryAddresses();
        ipFinder.setAddresses(addrs);
        spi.setIpFinder((TcpDiscoveryIpFinder)ipFinder);
        cfg.setDiscoverySpi((DiscoverySpi)spi);
        this.ignite = Ignition.start((IgniteConfiguration)cfg);
        this.usersCache = this.ignite.getOrCreateCache(new CacheConfiguration("stonks_sandbox_users"));
        this.positionsCache = this.ignite.getOrCreateCache(new CacheConfiguration("stonks_sandbox_positions"));
        this.tradesCache = this.ignite.getOrCreateCache(new CacheConfiguration("stonks_sandbox_trades"));
    }

    public IgniteCache<String, SandboxUser> usersCache() {
        return this.usersCache;
    }

    public IgniteCache<String, Position> positionsCache() {
        return this.positionsCache;
    }

    public IgniteCache<String, TradeRecord> tradesCache() {
        return this.tradesCache;
    }
}

