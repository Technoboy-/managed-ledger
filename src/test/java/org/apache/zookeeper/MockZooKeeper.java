package org.apache.zookeeper;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.zookeeper.AsyncCallback.Children2Callback;
import org.apache.zookeeper.AsyncCallback.ChildrenCallback;
import org.apache.zookeeper.AsyncCallback.DataCallback;
import org.apache.zookeeper.AsyncCallback.StatCallback;
import org.apache.zookeeper.AsyncCallback.StringCallback;
import org.apache.zookeeper.AsyncCallback.VoidCallback;
import org.apache.zookeeper.Watcher.Event.EventType;
import org.apache.zookeeper.Watcher.Event.KeeperState;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.internal.annotations.Sets;

import sun.reflect.ReflectionFactory;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.SetMultimap;
import com.google.inject.internal.Maps;

@SuppressWarnings({ "deprecation", "restriction", "rawtypes" })
public class MockZooKeeper extends ZooKeeper {
    private TreeMap<String, String> tree;
    private SetMultimap<String, Watcher> watchers;
    private AtomicBoolean stopped;

    public static ZooKeeper newInstance() {
        try {
            ReflectionFactory rf = ReflectionFactory.getReflectionFactory();
            Constructor objDef = Object.class.getDeclaredConstructor(new Class[0]);
            Constructor intConstr = rf.newConstructorForSerialization(MockZooKeeper.class, objDef);
            MockZooKeeper zk = MockZooKeeper.class.cast(intConstr.newInstance());
            zk.init();
            return zk;
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException("Cannot create object", e);
        }
    }

    private void init() {
        tree = Maps.newTreeMap();
        watchers = HashMultimap.create();
        stopped = new AtomicBoolean(false);
    }

    private MockZooKeeper(String quorum) throws Exception {
        // This constructor is never called
        super(quorum, 1, new Watcher() {
            @Override
            public void process(WatchedEvent event) {
            }
        });
        assert false;
    }

    @Override
    public States getState() {
        return States.CONNECTED;
    }

    @Override
    public synchronized String create(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws KeeperException,
            InterruptedException {
        if (stopped.get())
            throw new KeeperException.ConnectionLossException();

        if (tree.containsKey(path)) {
            throw new KeeperException.NodeExistsException(path);
        }
        tree.put(path, new String(data));
        return path;
    }

    @Override
    public synchronized void create(String path, byte[] data, List<ACL> acl, CreateMode createMode, StringCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.CONNECTIONLOSS.intValue(), path, ctx, null);
        } else if (tree.containsKey(path)) {
            cb.processResult(KeeperException.Code.NODEEXISTS.intValue(), path, ctx, null);
        } else {
            tree.put(path, new String(data));
            cb.processResult(0, path, ctx, null);
        }
    }

    @Override
    public synchronized byte[] getData(String path, Watcher watcher, Stat stat) throws KeeperException {
        String value = tree.get(path);
        if (value == null) {
            throw new KeeperException.NoNodeException(path);
        } else {
            if (watcher != null)
                watchers.put(path, watcher);
            return value.getBytes();
        }
    }

    @Override
    public synchronized void getData(String path, boolean watch, DataCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.ConnectionLoss, path, ctx, null, null);
            return;
        }

        String value = tree.get(path);
        if (value == null) {
            cb.processResult(KeeperException.Code.NoNode, path, ctx, null, null);
        } else {
            cb.processResult(0, path, ctx, value.getBytes(), new Stat());
        }
    }

    @Override
    public synchronized void getData(String path, Watcher watcher, DataCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.CONNECTIONLOSS.intValue(), path, ctx, null, null);
            return;
        }

        String value = tree.get(path);
        if (value == null) {
            cb.processResult(KeeperException.Code.NONODE.intValue(), path, ctx, null, null);
        } else {
            if (watcher != null)
                watchers.put(path, watcher);

            cb.processResult(0, path, ctx, value.getBytes(), new Stat());
        }
    }

    @Override
    public synchronized void getChildren(String path, Watcher watcher, ChildrenCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.ConnectionLoss, path, ctx, null);
            return;
        }

        List<String> children = Lists.newArrayList();
        for (String item : tree.tailMap(path).keySet()) {
            if (!item.startsWith(path)) {
                break;
            } else {
                String child = item.substring(path.length() + 1);
                if (!child.contains("/")) {
                    children.add(child);
                }
            }
        }

        cb.processResult(0, path, ctx, children);
        if (watcher != null)
            watchers.put(path, watcher);
    }

    @Override
    public synchronized List<String> getChildren(String path, boolean watch) throws KeeperException, InterruptedException {
        if (stopped.get()) {
            throw new KeeperException.ConnectionLossException();
        }

        List<String> children = Lists.newArrayList();
        for (String item : tree.tailMap(path).keySet()) {
            if (!item.startsWith(path)) {
                break;
            } else if (item.equals(path)) {
                continue;
            } else {
                String child = item.substring(path.length() + 1);
                log.debug("path: '{}' -- item: '{}' -- child: '{}'", new Object[] { path, item, child });
                if (!child.contains("/")) {
                    children.add(child);
                }
            }
        }

        return children;
    }

    @Override
    public synchronized void getChildren(String path, boolean watcher, Children2Callback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.ConnectionLoss, path, ctx, null, null);
            return;
        }

        log.debug("getChildren path={}", path);
        List<String> children = Lists.newArrayList();
        for (String item : tree.tailMap(path).keySet()) {
            log.debug("Checking path {}", item);
            if (!item.startsWith(path)) {
                break;
            } else if (item.equals(path)) {
                continue;
            } else {
                String child = item.substring(path.length() + 1);
                log.debug("child: '{}'", child);
                if (!child.contains("/")) {
                    children.add(child);
                }
            }
        }

        log.debug("getChildren done path={} result={}", path, children);
        cb.processResult(0, path, ctx, children, new Stat());
    }

    @Override
    public synchronized Stat exists(String path, boolean watch) throws KeeperException, InterruptedException {
        if (stopped.get())
            throw new KeeperException.ConnectionLossException();

        if (tree.containsKey(path)) {
            return new Stat();
        } else {
            return null;
        }
    }

    @Override
    public synchronized Stat setData(String path, byte[] data, int version) throws KeeperException, InterruptedException {
        if (stopped.get())
            throw new KeeperException.ConnectionLossException();

        tree.put(path, new String(data));
        Set<Watcher> toNotify = Sets.newHashSet();
        toNotify.addAll(watchers.get(path));
        watchers.removeAll(path);

        for (Watcher watcher : toNotify) {
            watcher.process(new WatchedEvent(EventType.NodeDataChanged, KeeperState.SyncConnected, path));
        }

        return new Stat();
    }

    @Override
    public synchronized void setData(String path, byte[] data, int version, StatCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.ConnectionLoss, path, ctx, null);
            return;
        }

        tree.put(path, new String(data));
        cb.processResult(0, path, ctx, new Stat());

        for (Watcher watcher : watchers.get(path)) {
            watcher.process(new WatchedEvent(EventType.NodeDataChanged, KeeperState.SyncConnected, path));
        }

        watchers.removeAll(path);
    }

    @Override
    public synchronized void delete(String path, int version) throws InterruptedException, KeeperException {
        if (stopped.get())
            throw new KeeperException.ConnectionLossException();
        if (!tree.containsKey(path))
            throw new KeeperException.NoNodeException(path);
        tree.remove(path);

        for (Watcher watcher : watchers.get(path)) {
            watcher.process(new WatchedEvent(EventType.NodeDeleted, KeeperState.SyncConnected, path));
        }

        watchers.removeAll(path);
    }

    @Override
    public synchronized void delete(String path, int version, VoidCallback cb, Object ctx) {
        if (stopped.get()) {
            cb.processResult(KeeperException.Code.CONNECTIONLOSS.intValue(), path, ctx);
        } else if (!tree.containsKey(path)) {
            cb.processResult(KeeperException.Code.NONODE.intValue(), path, ctx);
        } else {
            tree.remove(path);
            cb.processResult(0, path, ctx);

            for (Watcher watcher : watchers.get(path)) {
                watcher.process(new WatchedEvent(EventType.NodeDeleted, KeeperState.SyncConnected, path));
            }

            watchers.removeAll(path);
        }
    }

    @Override
    public synchronized void close() throws InterruptedException {
        stopped.set(true);
        tree.clear();
        watchers.clear();
    }

    private static final Logger log = LoggerFactory.getLogger(MockZooKeeper.class);
}
