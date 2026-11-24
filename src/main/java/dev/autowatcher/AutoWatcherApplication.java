package dev.autowatcher;

import java.nio.channels.*;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class AutoWatcherApplication {
    private static FileChannel lockChannel;
    private static FileLock lock;
    public static void main(String[] args) throws Exception {
        String override=System.getenv("AUTOWATCHER_DATA");
        String appData=System.getenv("APPDATA");
        Path directory=override!=null?Path.of(override):appData!=null?Path.of(appData,"AutoWatcher"):
            Path.of(System.getProperty("user.home"),".autowatcher");
        Files.createDirectories(directory);
        try { Files.setPosixFilePermissions(directory,PosixFilePermissions.fromString("rwx------")); }
        catch(UnsupportedOperationException ignored) { }
        System.setProperty("autowatcher.data-dir",directory.toAbsolutePath().toString());
        Files.writeString(directory.resolve("LogFile.log"),System.lineSeparator()+java.time.Instant.now()
            +" Старт PID="+ProcessHandle.current().pid()+" программа="+AutoWatcherApplication.class.getProtectionDomain().getCodeSource().getLocation()
            +System.lineSeparator(),StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        lockChannel=FileChannel.open(directory.resolve("application.lock"),StandardOpenOption.CREATE,StandardOpenOption.WRITE);
        lock=lockChannel.tryLock();
        if(lock==null) { Files.writeString(directory.resolve("LogFile.log"),"Запуск отменён: уже работает другой экземпляр AutoWatcher"+System.lineSeparator(),StandardOpenOption.APPEND); throw new IllegalStateException("AutoWatcher уже запущен. Открой http://127.0.0.1:8080"); }
        Runtime.getRuntime().addShutdownHook(new Thread(()->{
            try { lock.release();lockChannel.close(); } catch(Exception ignored) { }
        }));
        String database=System.getenv("AUTOWATCHER_DB");
        if(database!=null && Path.of(database).toAbsolutePath().getParent()!=null)
            Files.createDirectories(Path.of(database).toAbsolutePath().getParent());
        SpringApplication.run(AutoWatcherApplication.class,args);
    }
}
