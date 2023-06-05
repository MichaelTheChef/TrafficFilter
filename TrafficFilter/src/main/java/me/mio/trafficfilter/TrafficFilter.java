package me.mio.trafficfilter;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent.Result;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class TrafficFilter extends JavaPlugin implements Listener {
    private Map<String, Integer> connectionAttempts;
    private Map<String, Long> lastConnectionTimes;
    private Connection databaseConnection;
    private Logger logger;

    @Override
    public void onEnable() {
        connectionAttempts = new HashMap<>();
        lastConnectionTimes = new HashMap<>();
        logger = getLogger();

        try {
            String url = "jdbc:mysql://localhost:3306/your_database_name";
            String username = "your_username";
            String password = "your_password";
            databaseConnection = DriverManager.getConnection(url, username, password);
            createConnectionAttemptsTable();
            loadConnectionAttempts();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "An error occurred while establishing the database connection", e);
        }

        getServer().getPluginManager().registerEvents(this, this);
    }

    @Override
    public void onDisable() {
        connectionAttempts.clear();
        lastConnectionTimes.clear();
        try {
            databaseConnection.close();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "An error occurred while closing the database connection", e);
        }
    }

    @EventHandler
    public void onPlayerLogin(PlayerLoginEvent event) {
        String ipAddress = event.getAddress().getHostAddress();

        if (isBlocked(ipAddress)) {
            event.disallow(Result.KICK_OTHER, "You are blocked from connecting to this server.");
            return;
        }

        int attempts = connectionAttempts.getOrDefault(ipAddress, 0) + 1;
        connectionAttempts.put(ipAddress, attempts);
        lastConnectionTimes.put(ipAddress, System.currentTimeMillis());

        if (attempts >= 5 && hasExcessiveAttempts(ipAddress) && hasRapidAttempts(ipAddress)) {
            blockIP(ipAddress);
            event.disallow(Result.KICK_OTHER, "You are blocked from connecting to this server.");
        }
    }

    private boolean isBlocked(String ipAddress) {
        return getBlockedIPs().contains(ipAddress);
    }

    private boolean hasExcessiveAttempts(String ipAddress) {
        int maxAttempts = 10;
        long timePeriodMillis = TimeUnit.MINUTES.toMillis(5);
        long currentTime = System.currentTimeMillis();

        int attempts = 0;
        long startTime = currentTime - timePeriodMillis;

        for (Map.Entry<String, Long> entry : lastConnectionTimes.entrySet()) {
            if (entry.getValue() >= startTime && entry.getValue() <= currentTime) {
                attempts++;
            }
        }

        return attempts >= maxAttempts;
    }

    private boolean hasRapidAttempts(String ipAddress) {
        int maxAttempts = 3;
        long timePeriodMillis = TimeUnit.SECONDS.toMillis(10);
        long currentTime = System.currentTimeMillis();

        int attempts = 0;
        long startTime = currentTime - timePeriodMillis;

        for (Map.Entry<String, Long> entry : lastConnectionTimes.entrySet()) {
            if (entry.getValue() >= startTime && entry.getValue() <= currentTime) {
                attempts++;
            }
        }

        return attempts >= maxAttempts;
    }

    private void blockIP(String ipAddress) {
        getBlockedIPs().add(ipAddress);

        try {
            PreparedStatement statement = databaseConnection.prepareStatement("INSERT INTO blocked_ips (ip_address) VALUES (?)");
            statement.setString(1, ipAddress);
            statement.executeUpdate();
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "An error occurred while blocking the IP address", e);
        }
    }

    private Set<String> getBlockedIPs() {
        Set<String> blockedIPs = new HashSet<>();
        try {
            PreparedStatement statement = databaseConnection.prepareStatement("SELECT ip_address FROM blocked_ips");
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String ipAddress = resultSet.getString("ip_address");
                blockedIPs.add(ipAddress);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "An error occurred while retrieving blocked IPs", e);
        }
        return blockedIPs;
    }

    private void createConnectionAttemptsTable() throws SQLException {
        PreparedStatement statement = databaseConnection.prepareStatement("CREATE TABLE IF NOT EXISTS connection_attempts (id INT AUTO_INCREMENT PRIMARY KEY, ip_address VARCHAR(255) UNIQUE, attempts INT)");
        statement.executeUpdate();
    }

    private void loadConnectionAttempts() {
        try {
            PreparedStatement statement = databaseConnection.prepareStatement("SELECT ip_address, attempts FROM connection_attempts");
            ResultSet resultSet = statement.executeQuery();
            while (resultSet.next()) {
                String ipAddress = resultSet.getString("ip_address");
                int attempts = resultSet.getInt("attempts");
                connectionAttempts.put(ipAddress, attempts);
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "An error occurred while loading connection attempts", e);
        }
    }
}
