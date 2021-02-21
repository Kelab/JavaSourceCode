/*
 * ORACLE PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */

/*
 *
 *
 *
 *
 *
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent.locks;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import sun.misc.Unsafe;

/**
 * 提供一个框架来实现依赖于先进先出（FIFO）等待队列的阻塞锁和相关的同步器（信号灯，事件等）。
 * 此类旨在为大多数依赖单个原子int值表示状态的同步器提供有用的基础。
 * 子类必须定义更改此状态的受保护方法，并定义该状态对于获取或释放此对象而言意味着什么。
 *
 * 鉴于这些，此类中的其他方法将执行所有排队和阻塞机制。
 *
 * 子类可以维护其他状态字段，但是相对于同步，
 * 仅跟踪使用方法getState ， setState和compareAndSetState操作的原子更新的int值。
 * 子类应定义为用于实现其所在类的同步属性的非公共内部帮助器类。
 * 类AbstractQueuedSynchronizer不实现任何同步接口。 而是定义了诸如acquireInterruptibly方法，
 * 可以通过具体的锁和相关的同步器适当地调用这些方法来实现其公共方法。
 *
 * 此类支持默认排他模式和共享模式之一或两者。
 *
 * 当以独占方式进行获取时，其他线程尝试进行的获取将无法成功。
 *
 * 由多个线程获取的共享模式可能（但不一定）成功。
 *
 * 该类不“理解”这些差异，只是从机械意义上说，当成功获取共享模式时，
 * 下一个等待线程（如果存在）也必须确定它是否也可以获取。 在不同模式下等待的线程共享相同的FIFO队列。
 *
 * 通常，实现子类仅支持这些模式之一，但例如可以在ReadWriteLock发挥作用。
 *
 * 仅支持独占模式或仅支持共享模式的子类无需定义支持未使用模式的方法。
 *
 * 此类定义了一个嵌套的AbstractQueuedSynchronizer.ConditionObject类，
 * 该类可以被支持独占模式的子类用作Condition实现，
 * isHeldExclusively独占方式isHeldExclusively报告是否相对于当前线程独占同步，
 * 使用当前getState值调用的方法release可以完全释放此线程。对象，并在acquire此保存状态值的情况下acquire ，
 * 最终将该对象恢复为其先前获取的状态。
 *
 * 否则，没有AbstractQueuedSynchronizer方法会创建这样的条件，因此，如果无法满足此约束，请不要使用它。
 *
 * 当然，AbstractQueuedSynchronizer.ConditionObject的行为取决于其同步器实现的语义。
 * 此类提供了内部队列的检查，检测和监视方法，以及条件对象的类似方法。
 *
 * 可以根据需要使用AbstractQueuedSynchronizer将它们导出到类中以实现其同步机制。
 * 此类的序列化仅存储基本的原子整数维护状态，因此反序列化的对象具有空线程队列。
 *
 * 需要可序列化的典型子类将定义一个readObject方法，该方法将在反序列化时将其恢复为已知的初始状态。
 * 用法
 * 要将此类用作同步器的基础，请使用getState ， setState和/或compareAndSetState检查和/或修改同步状态，以重新定义以下方法（如适用）：
 * tryAcquire
 * tryRelease
 * tryAcquireShared
 * tryReleaseShared
 * isHeldExclusively
 * 默认情况下，这些方法中的每一个都会引发UnsupportedOperationException 。
 *
 * 这些方法的实现必须在内部是线程安全的，并且通常应简短且不阻塞。
 *
 * 定义这些方法是使用此类的唯一受支持的方法。
 *
 * 所有其他方法都被声明为final方法，因为它们不能独立变化。
 * 您可能还会发现从AbstractOwnableSynchronizer继承的方法对于跟踪拥有独占同步器的线程很有用。
 *
 * 鼓励您使用它们-这将启用监视和诊断工具，以帮助用户确定哪些线程持有锁。
 * 即使此类基于内部FIFO队列，它也不会自动执行FIFO获取策略。 独占同步的核心采用以下形式：
 *    Acquire:
 *        while (!tryAcquire(arg)) {
 *           enqueue thread if it is not already queued;
 *           possibly block current thread;
 *        }
 *
 *    Release:
 *        if (tryRelease(arg))
 *           unblock the first queued thread;
 *
 * （共享模式相似，但可能涉及级联信号。）
 * 因为获取队列中的获取检查是在排队之前被调用的，所以新获取线程可能在被阻塞和排队的其他线程之前插入。
 *
 * 但是，如果需要，您可以定义tryAcquire和/或tryAcquireShared以通过内部调用一种或多种检查方法来禁用插入，
 * 从而提供公平的FIFO获取顺序。 特别是，如果hasQueuedPredecessors （一种专门设计用于公平同步器的方法）
 * 返回true ，则大多数公平同步器都可以定义tryAcquire返回false 。
 *
 * 其他变化是可能的。
 * 对于默认插入（也称为greedy ， retouncement和convoy-avoidance ）策略，吞吐量和可伸缩性通常最高。
 *
 * 尽管不能保证这是公平的，也可以避免饥饿，但是允许在较早排队的线程之前对较早排队的线程进行重新竞争，
 * 并且每个重新争用都可以毫无偏向地成功抵御传入线程。 同样，尽管获取通常不会“旋转”，
 * 但它们可能会在阻塞之前执行tryAcquire多次调用，并与其他计算穿插。 如果仅短暂地保持排他同步，
 * 则这将带来旋转的大部分好处，而在没有同步时，则不会带来很多负担。
 *
 * 如果需要的话，可以通过在调用之前对获取方法进行“快速路径”检查来增强此功能，
 * 并可能预先检查hasContended和/或hasQueuedThreads仅在可能不争用同步器的情况下才这样做。
 *
 * 此类为同步提供了有效且可扩展的基础，部分原因是该类的使用范围专门用于可以依赖于int状态，获取和释放参数以及内部FIFO等待队列的同步器。 如果这还不足够，则可以使用atomic类，您自己的自定义java.util.Queue类和LockSupport阻止支持从较低级别构建同步器。
 * 使用范例
 *
 * 这是一个不可重入的互斥锁定类，使用值0表示解锁状态，使用值1表示锁定状态。 虽然不可重入锁并不严格要求记录当前所有者线程，但是无论如何，此类都这样做以使使用情况更易于监视。 它还支持条件并公开一种检测方法：
 *
 *  class Mutex implements Lock, java.io.Serializable {
 *
 *    // Our internal helper class
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *      // Reports whether in locked state
 *      protected boolean isHeldExclusively() {
 *        return getState() == 1;
 *      }
 *
 *      // Acquires the lock if state is zero
 *      public boolean tryAcquire(int acquires) {
 *        assert acquires == 1; // Otherwise unused
 *        if (compareAndSetState(0, 1)) {
 *          setExclusiveOwnerThread(Thread.currentThread());
 *          return true;
 *        }
 *        return false;
 *      }
 *
 *      // Releases the lock by setting state to zero
 *      protected boolean tryRelease(int releases) {
 *        assert releases == 1; // Otherwise unused
 *        if (getState() == 0) throw new IllegalMonitorStateException();
 *        setExclusiveOwnerThread(null);
 *        setState(0);
 *        return true;
 *      }
 *
 *      // Provides a Condition
 *      Condition newCondition() { return new ConditionObject(); }
 *
 *      // Deserializes properly
 *      private void readObject(ObjectInputStream s)
 *          throws IOException, ClassNotFoundException {
 *        s.defaultReadObject();
 *        setState(0); // reset to unlocked state
 *      }
 *    }
 *
 *    // The sync object does all the hard work. We just forward to it.
 *    private final Sync sync = new Sync();
 *
 *    public void lock()                { sync.acquire(1); }
 *    public boolean tryLock()          { return sync.tryAcquire(1); }
 *    public void unlock()              { sync.release(1); }
 *    public Condition newCondition()   { return sync.newCondition(); }
 *    public boolean isLocked()         { return sync.isHeldExclusively(); }
 *    public boolean hasQueuedThreads() { return sync.hasQueuedThreads(); }
 *    public void lockInterruptibly() throws InterruptedException {
 *      sync.acquireInterruptibly(1);
 *    }
 *    public boolean tryLock(long timeout, TimeUnit unit)
 *        throws InterruptedException {
 *      return sync.tryAcquireNanos(1, unit.toNanos(timeout));
 *    }
 *  }
 * 这是一个类似于CountDownLatch的闩锁类，只不过它只需要触发一个signal即可。 因为闩锁是非排他性的，所以它使用shared获取和释放方法。
 *
 *  class BooleanLatch {
 *
 *    private static class Sync extends AbstractQueuedSynchronizer {
 *      boolean isSignalled() { return getState() != 0; }
 *
 *      protected int tryAcquireShared(int ignore) {
 *        return isSignalled() ? 1 : -1;
 *      }
 *
 *      protected boolean tryReleaseShared(int ignore) {
 *        setState(1);
 *        return true;
 *      }
 *    }
 *
 *    private final Sync sync = new Sync();
 *    public boolean isSignalled() { return sync.isSignalled(); }
 *    public void signal()         { sync.releaseShared(1); }
 *    public void await() throws InterruptedException {
 *      sync.acquireSharedInterruptibly(1);
 *    }
 *  }
 *
 * @since 1.5
 * @author Doug Lea
 */
public abstract class AbstractQueuedSynchronizer
    extends AbstractOwnableSynchronizer
    implements java.io.Serializable {

    private static final long serialVersionUID = 7373984972572414691L;

    /**
     * Creates a new {@code AbstractQueuedSynchronizer} instance
     * with initial synchronization state of zero.
     */
    protected AbstractQueuedSynchronizer() { }

    /**
     * 等待队列节点类。
     * 等待队列是“ CLH”（Craig，Landin和Hagersten）锁定队列的变体。
     *
     * CLH锁通常用于自旋锁。
     *
     * 相反，我们将它们用于阻塞同步器，但使用相同的基本策略，即将有关线程的某些控制信息保存在其节点的前身中。
     * 每个节点中的“状态”字段将跟踪线程是否应阻塞。
     * 节点的前任释放时会发出信号。
     * 否则，队列的每个节点都充当一个特定通知样式的监视器，其中包含一个等待线程。
     * 虽然状态字段不控制是否授予线程锁等。 线程可能会尝试获取它是否在队列中的第一位。
     * 但是先行并不能保证成功。
     * 它只赋予竞争权。
     * 因此，当前发布的竞争者线程可能需要重新等待。
     * 要加入CLH锁，您可以自动将其作为新尾部拼接。
     * 要出队，您只需设置头字段。
     *             +------+  prev +-----+       +-----+
     *        head |      | <---- |     | <---- |     |  tail
     *             +------+       +-----+       +-----+
     *
     * 插入到CLH队列中只需要对“尾巴”执行一次原子操作，因此从未排队到排队有一个简单的分界点。
     * 成功CAS后，排队线程将设置前身的“下一个”链接。
     * 即使不是原子的，这也足以确保任何阻塞的线程在合格时由前任发出信号
     * （尽管在取消的情况下，可能借助方法cleanQueue中的信号）。
     *
     * 信令部分基于类似Dekker的方案，其中待等待线程指示等待状态，然后重试获取，然后在阻塞之前重新检查状态。
     * 解除停车时，信号器自动清除等待状态。
     * 在获取时出队涉及分离（清零）节点的“上一个”节点，然后更新“头”。
     * 其他线程通过检查“上一个”而不是头部来检查节点是否已出队。
     * 如果需要，我们通过旋转等待来强制执行清零然后设置顺序。
     * 因此，锁定算法本身并不是严格“无锁定”的，因为获取线程可能需要等待先前的获取才能取得进展。
     *
     * 与排他锁一起使用时，无论如何都需要这样的进度。
     * 但是，共享模式可能（不常见）需要在设置头字段之前进行旋转等待，以确保正确传播。
     * （历史记录：与此类的先前版本相比，它可以简化一些步骤并提高效率。）
     * 节点的前任节点可以在等待期间由于取消而更改，直到该节点排在队列中为止，
     * 此时节点不能更改。获取方法通过在等待之前重新检查“ prev”来解决此问题。
     *
     * prev和next字段仅通过方法cleanQueue中被取消的节点通过CAS进行修改。
     * 取消拼接策略使人联想到Michael-Scott队列，因为成功完成CAS的prev字段后，
     * 其他线程将帮助修复下一个字段。由于取消通常是一堆束的事情，使有关必要信号的决策变得复杂，
     * 因此对cleanQueue的每次调用都会遍历队列，直到进行干净扫描为止。
     * 首先重新链接的节点将无条件地停放（有时是不必要的，但是这些情况不值得避免）。
     *
     * 线程可能会尝试获取它是否在队列中的第一个（最前面），有时甚至是之前。
     * 成为第一并不保证成功。它只赋予竞争权。
     * 通过在排队过程中允许传入线程“插入”并获取同步器，我们平衡了吞吐量，开销和公平性，
     * 在这种情况下，唤醒的第一个线程可能需要重新等待。为了抵消可能的重复不幸的重新等待，
     * 我们成倍地增加了重试次数（最多256次），以便每次释放线程时都进行重试。
     * 除非在这种情况下，否则AQS锁不会旋转。相反，他们交错尝试通过簿记步骤进行获取。
     * （想要自旋锁的用户可以使用tryAcquire。）为了提高垃圾回收能力，尚未列出的节点的字段为空。
     * （创建并丢弃而不使用它的节点并不罕见。）列表中出现的节点字段会尽快被清空。
     * 这加剧了从外部确定第一个等待线程的挑战（如getFirstQueuedThread方法中那样）。
     * 有时，当字段显示为空时，需要回退从原子更新的“尾部”向后遍历的回退。
     * （但是，这在发信号的过程中永远不需要。）CLH队列需要一个虚拟标头节点来开始。
     * 但是我们不会在构建过程中创建它们，因为如果没有争用，这将是浪费时间。
     * 而是构造节点，并在第一次争用时设置头和尾指针。共享模式操作与“独占”的区别在于，
     * 获取信号会通知下一个服务员是否也要共享。
     * tryAcquireShared API允许用户指示传播程度，但是在大多数应用程序中，忽略此传播的效率更高，
     * 允许后继者在任何情况下尝试进行获取。
     *
     * 等待条件的线程使用具有附加链接的节点来维护条件的（FIFO）列表。
     * 条件只需要在简单（非并行）链接队列中链接节点，因为仅当它们专用时才可以访问它们。
     * 等待时，将节点插入条件队列。收到信号后，该节点就排队进入主队列。
     * 一个特殊的状态字段值用于跟踪并自动触发该值。
     * 对头，尾和状态字段的访问使用完整的挥发模式以及CAS。
     * 节点字段的状态，上一个和下一个也是如此，尽管线程可能是可发信号的，
     * 但有时使用弱模式。对字段“ waiter”（要发出信号的线程）的访问始终被夹在其他原子访问之间，
     * 因此在普通模式下使用。
     * 我们使用原子访问方法的jdk.internal Unsafe版本而不是VarHandles来避免潜在的VM引导问题。
     * 上面的大多数操作是通过主要的内部方法获取执行的，所有已导出的获取方法都以某种方式调用该方法。
     * （对于频繁使用的编译器来说，通常通常很容易优化调用站点的特殊化。）
     * 关于何时以及如何在阻塞之前和之后检查获取和等待中的中断的时间和方式，
     * 有几个任意的决定。这些决定在实现更新中不太随意，因为某些用户似乎以原始的方式依赖原始行为，
     * 因此通常（很少）犯错，但很难为变更辩护。
     *
     * 感谢Dave Dice，Mark Moir，Victor Luchangco，Bill Scherer和Michael Scott以及JSR-166专家组的成员，对本课程的设计提出了有益的想法，讨论和批评。
     */
    static final class Node {
        /** 指示节点正在以共享模式等待的标记 */
        static final Node SHARED = new Node();
        /** 指示节点正在以独占模式等待的标记 */
        static final Node EXCLUSIVE = null;

        /** waitStatus值，指示线程已取消 */
        static final int CANCELLED =  1;
        /** waitStatus值，指示后续线程需要释放  */
        static final int SIGNAL    = -1;
        /** waitStatus值，指示线程正在等待条件 */
        static final int CONDITION = -2;
        /**
         * waitStatus值，指示下一个acquireShared应该无条件传播
         */
        static final int PROPAGATE = -3;

        /**
         * 状态字段，仅采用以下值：
         * SIGNAL：
         *      此节点的后继者被（或将很快被）阻止（通过停放），因此当前节点在释放或取消时必须取消其后继者的停放。
         *      为避免种族冲突，acquire方法必须首先指示它们需要信号，然后重试原子获取，然后在失败时阻塞。
         * CANCELLED：
         *      由于超时或中断，该节点被取消。
         *      节点永远不会离开此状态。
         *      特别是，具有取消节点的线程永远不会再次阻塞。
         * CONDITION：
         *      该节点当前在条件队列中。
         *      在传输之前，它不会用作同步队列节点，此时状态将设置为0。（此值的使用与该字段的其他用途无关，但简化了机制。）
         * PROPAGATE：
         *      A releaseShared应该传播到其他节点。
         *      在doReleaseShared中对此进行了设置（仅适用于头节点），以确保传播继续进行，即使此后进行了其他操作也是如此。
         * 0：
         *      以上都不是。
         *
         * 为了简化使用，数值以数字方式排列。
         * 非负值表示节点不需要发信号。
         * 因此，大多数代码不需要检查特定值，仅需检查符号即可。
         * 对于常规同步节点，该字段初始化为0，对于条件节点，该字段初始化为CONDITION。
         * 使用CAS（或在可能的情况下进行无条件的易失性写操作）对其进行修改。
         */
        volatile int waitStatus;

        /**
         * 链接到当前节点线程用来检查waitStatus的先前节点。在入队期间分配，
         * 并且仅在出队时将其清空（出于GC的考虑）。
         * 同样，在取消前任后，我们会短路，同时找到一个未取消的前任，
         * 这将一直存在，因为根节点永远不会被取消：只有成功获取后，
         * 结点才变为根。被取消的线程永远不会成功获取，并且一个线程只会取消自身，
         * 而不会取消任何其他节点。
         */
        volatile Node prev;

        /**
         * 链接到当前节点线程在释放时将停放的后继节点。
         * 在排队过程中分配，在绕过取消的前任对象时进行调整，在出队时无效（出于GC的考虑）。
         * enq操作直到附加后才分配前任的下一个字段，因此看到空的下一个字段不一定表示节点在队列末尾。
         * 但是，如果下一个字段似乎为空，则我们可以从尾部扫描上一个以进行再次检查。
         * 被取消节点的下一个字段设置为指向节点本身而不是null，以使isOnSyncQueue的工作更轻松。
         */
        volatile Node next;

        /**
         * The thread that enqueued this node.  Initialized on
         * construction and nulled out after use.
         */
        volatile Thread thread;

        /**
         * 链接到等待条件的下一个节点，或者链接到特殊值SHARED。
         * 由于条件队列仅在以独占模式保存时才被访问，
         * 因此我们只需要一个简单的链接队列即可在节点等待条件时保存节点。
         * 然后将它们转移到队列中以重新获取。由于条件只能是互斥的，
         * 因此我们使用特殊值来表示共享模式来保存字段。
         */
        Node nextWaiter;

        /**
         * Returns true if node is waiting in shared mode.
         */
        final boolean isShared() {
            return nextWaiter == SHARED;
        }

        /**
         * Returns previous node, or throws NullPointerException if null.
         * Use when predecessor cannot be null.  The null check could
         * be elided, but is present to help the VM.
         *
         * @return the predecessor of this node
         */
        final Node predecessor() throws NullPointerException {
            Node p = prev;
            if (p == null)
                throw new NullPointerException();
            else
                return p;
        }

        Node() {    // Used to establish initial head or SHARED marker
        }

        Node(Thread thread, Node mode) {     // Used by addWaiter
            this.nextWaiter = mode;
            this.thread = thread;
        }

        Node(Thread thread, int waitStatus) { // Used by Condition
            this.waitStatus = waitStatus;
            this.thread = thread;
        }
    }

    /**
     * Head of the wait queue, lazily initialized.  Except for
     * initialization, it is modified only via method setHead.  Note:
     * If head exists, its waitStatus is guaranteed not to be
     * CANCELLED.
     */
    private transient volatile Node head;

    /**
     * Tail of the wait queue, lazily initialized.  Modified only via
     * method enq to add new wait node.
     */
    private transient volatile Node tail;

    /**
     * The synchronization state.
     */
    private volatile int state;

    /**
     * Returns the current value of synchronization state.
     * This operation has memory semantics of a {@code volatile} read.
     * @return current state value
     */
    protected final int getState() {
        return state;
    }

    /**
     * Sets the value of synchronization state.
     * This operation has memory semantics of a {@code volatile} write.
     * @param newState the new state value
     */
    protected final void setState(int newState) {
        state = newState;
    }

    /**
     * Atomically sets synchronization state to the given updated
     * value if the current state value equals the expected value.
     * This operation has memory semantics of a {@code volatile} read
     * and write.
     *
     * @param expect the expected value
     * @param update the new value
     * @return {@code true} if successful. False return indicates that the actual
     *         value was not equal to the expected value.
     */
    protected final boolean compareAndSetState(int expect, int update) {
        // See below for intrinsics setup to support this
        return unsafe.compareAndSwapInt(this, stateOffset, expect, update);
    }

    // Queuing utilities

    /**
     * The number of nanoseconds for which it is faster to spin
     * rather than to use timed park. A rough estimate suffices
     * to improve responsiveness with very short timeouts.
     */
    static final long spinForTimeoutThreshold = 1000L;

    /**
     * Inserts node into queue, initializing if necessary. See picture above.
     * @param node the node to insert
     * @return node's predecessor
     */
    private Node enq(final Node node) {
        for (;;) {
            Node t = tail;
            if (t == null) { // Must initialize
                if (compareAndSetHead(new Node()))
                    tail = head;
            } else {
                node.prev = t;
                if (compareAndSetTail(t, node)) {
                    t.next = node;
                    return t;
                }
            }
        }
    }

    /**
     * Creates and enqueues node for current thread and given mode.
     *
     * @param mode Node.EXCLUSIVE for exclusive, Node.SHARED for shared
     * @return the new node
     */
    private Node addWaiter(Node mode) {
        Node node = new Node(Thread.currentThread(), mode);
        // Try the fast path of enq; backup to full enq on failure
        Node pred = tail;
        if (pred != null) {
            node.prev = pred;
            if (compareAndSetTail(pred, node)) {
                pred.next = node;
                return node;
            }
        }
        enq(node);
        return node;
    }

    /**
     * Sets head of queue to be node, thus dequeuing. Called only by
     * acquire methods.  Also nulls out unused fields for sake of GC
     * and to suppress unnecessary signals and traversals.
     *
     * @param node the node
     */
    private void setHead(Node node) {
        head = node;
        node.thread = null;
        node.prev = null;
    }

    /**
     * 唤醒节点的后继者（如果存在）。
     *
     * @param node the node
     */
    private void unparkSuccessor(Node node) {
        /*
         * 如果状态是否定的（即可能需要信号），请尝试清除以预期发出信号。
         * 如果失败或等待线程更改状态，则可以。
         */
        int ws = node.waitStatus;
        if (ws < 0)
            compareAndSetWaitStatus(node, ws, 0);

        /*
         * 释放线程将保留在后续线程中，该线程通常只是下一个节点。
         * 但是，如果已取消或明显为空，请从尾部向后移动以找到实际的未取消后继。
         */
        Node s = node.next;
        //waitStatus表示该线程已经取消（只有cancel是大于0的）
        if (s == null || s.waitStatus > 0) {
            //若后继节点是空的，或者这个节点已经被取消了，那么需要尝试寻找一个合适的节点
            s = null;
            //从后往前遍历，不断更新s（要唤醒的线程）
            for (Node t = tail; t != null && t != node; t = t.prev)
                if (t.waitStatus <= 0)
                    s = t;
        }
        if (s != null)
            //唤醒一个线程（unpark方法是将线程从阻塞状态唤醒）
            LockSupport.unpark(s.thread);
    }

    /**
     * Release action for shared mode -- signals successor and ensures
     * propagation. (Note: For exclusive mode, release just amounts
     * to calling unparkSuccessor of head if it needs signal.)
     */
    private void doReleaseShared() {
        /*
         * Ensure that a release propagates, even if there are other
         * in-progress acquires/releases.  This proceeds in the usual
         * way of trying to unparkSuccessor of head if it needs
         * signal. But if it does not, status is set to PROPAGATE to
         * ensure that upon release, propagation continues.
         * Additionally, we must loop in case a new node is added
         * while we are doing this. Also, unlike other uses of
         * unparkSuccessor, we need to know if CAS to reset status
         * fails, if so rechecking.
         */
        for (;;) {
            Node h = head;
            if (h != null && h != tail) {
                int ws = h.waitStatus;
                if (ws == Node.SIGNAL) {
                    if (!compareAndSetWaitStatus(h, Node.SIGNAL, 0))
                        continue;            // loop to recheck cases
                    unparkSuccessor(h);
                }
                else if (ws == 0 &&
                         !compareAndSetWaitStatus(h, 0, Node.PROPAGATE))
                    continue;                // loop on failed CAS
            }
            if (h == head)                   // loop if head changed
                break;
        }
    }

    /**
     * Sets head of queue, and checks if successor may be waiting
     * in shared mode, if so propagating if either propagate > 0 or
     * PROPAGATE status was set.
     *
     * @param node the node
     * @param propagate the return value from a tryAcquireShared
     */
    private void setHeadAndPropagate(Node node, int propagate) {
        Node h = head; // Record old head for check below
        setHead(node);
        /*
         * Try to signal next queued node if:
         *   Propagation was indicated by caller,
         *     or was recorded (as h.waitStatus either before
         *     or after setHead) by a previous operation
         *     (note: this uses sign-check of waitStatus because
         *      PROPAGATE status may transition to SIGNAL.)
         * and
         *   The next node is waiting in shared mode,
         *     or we don't know, because it appears null
         *
         * The conservatism in both of these checks may cause
         * unnecessary wake-ups, but only when there are multiple
         * racing acquires/releases, so most need signals now or soon
         * anyway.
         */
        if (propagate > 0 || h == null || h.waitStatus < 0 ||
            (h = head) == null || h.waitStatus < 0) {
            Node s = node.next;
            if (s == null || s.isShared())
                doReleaseShared();
        }
    }

    // Utilities for various versions of acquire

    /**
     * Cancels an ongoing attempt to acquire.
     *
     * @param node the node
     */
    private void cancelAcquire(Node node) {
        // Ignore if node doesn't exist
        if (node == null)
            return;

        node.thread = null;

        // Skip cancelled predecessors
        Node pred = node.prev;
        while (pred.waitStatus > 0)
            node.prev = pred = pred.prev;

        // predNext is the apparent node to unsplice. CASes below will
        // fail if not, in which case, we lost race vs another cancel
        // or signal, so no further action is necessary.
        Node predNext = pred.next;

        // Can use unconditional write instead of CAS here.
        // After this atomic step, other Nodes can skip past us.
        // Before, we are free of interference from other threads.
        node.waitStatus = Node.CANCELLED;

        // If we are the tail, remove ourselves.
        if (node == tail && compareAndSetTail(node, pred)) {
            compareAndSetNext(pred, predNext, null);
        } else {
            // If successor needs signal, try to set pred's next-link
            // so it will get one. Otherwise wake it up to propagate.
            int ws;
            if (pred != head &&
                ((ws = pred.waitStatus) == Node.SIGNAL ||
                 (ws <= 0 && compareAndSetWaitStatus(pred, ws, Node.SIGNAL))) &&
                pred.thread != null) {
                Node next = node.next;
                if (next != null && next.waitStatus <= 0)
                    compareAndSetNext(pred, predNext, next);
            } else {
                unparkSuccessor(node);
            }

            node.next = node; // help GC
        }
    }

    /**
     *
     * 判断当前这个节点是不是应该阻塞起来
     *
     * 检查并更新无法获取的节点的状态。
     * 如果线程应阻塞，则返回true。
     * 这是所有采集循环中的主要信号控制。
     * 要求pred == node.prev。
     *
     * 前者为signal，阻塞
     * 前者为cancel，更新前者
     * 其他情况：更新前者为signal
     *
     * @param pred node's predecessor holding status
     * @param node the node
     * @return {@code true} if thread should block
     */
    private static boolean shouldParkAfterFailedAcquire(Node pred, Node node) {
        int ws = pred.waitStatus;
        if (ws == Node.SIGNAL)
            /*
             * 该节点已经设置了状态，要求释放以发出信号，以便可以安全地停放
             */
            return true;
        if (ws > 0) {
            /*
             * 若前者是取消的，
             * 则继续向前找并更新记录中的前者
             */
            do {
                node.prev = pred = pred.prev;
            } while (pred.waitStatus > 0);
            pred.next = node;
        } else {
            /*
             * waitStatus一定为0或PROPAGATE。
             * 表示我们需要一个信号，但不要阻塞。
             * 呼叫者将需要重试以确保在阻塞前无法获取
             */
            //尝试修改状态位signal,下次访问时就会阻塞
            compareAndSetWaitStatus(pred, ws, Node.SIGNAL);
        }
        return false;
    }

    /**
     * Convenience method to interrupt current thread.
     */
    static void selfInterrupt() {
        Thread.currentThread().interrupt();
    }

    /**
     * Convenience method to park and then check if interrupted
     *
     * @return {@code true} if interrupted
     */
    private final boolean parkAndCheckInterrupt() {
        LockSupport.park(this);
        return Thread.interrupted();
    }

    /*
     * 各种获取方式，包括互斥共享和控制方式。
     * 每个都基本相同，但令人讨厌的不同。
     * 由于异常机制（包括确保在tryAcquire抛出异常时我们取消）和其他控件的相互作用，
     * 因此只有少量分解是可能的，至少在不影响性能的前提下。
     */

    /**
     * 以排他的不间断模式获取已在队列中的线程。
     * 用于条件等待方法以及获取。
     *
     * 当一个线程执行完任务后，不会删除自己，而是保持在Head上
     * 此时后续节点用cas进行争夺，成功后更新head
     * @param node the node
     * @param arg the acquire argument
     * @return {@code true} if interrupted while waiting
     */
    final boolean acquireQueued(final Node node, int arg) {
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                //获取之前的节点
                final Node p = node.predecessor();
                //如果p==Head表示自己在队列的第一位，
                // tryAcquire()为尝试获取锁，为子类实现，若成功则表示抢到了锁的权限（CAS操作）
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return interrupted;
                }
                // 若需要阻塞，则进行阻塞操作，并设置interrupt为true，线程将在这里阻塞，
                // 直到有锁的释放，才会唤醒第一个等待的。
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            //若发生了异常，导致添加了队列，但是异常弹出了
            //这里需要删除创建的节点
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return;
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in exclusive timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.EXCLUSIVE);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head && tryAcquire(arg)) {
                    setHead(node);
                    p.next = null; // help GC
                    failed = false;
                    return true;
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared uninterruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireShared(int arg) {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            boolean interrupted = false;
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        if (interrupted)
                            selfInterrupt();
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    interrupted = true;
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared interruptible mode.
     * @param arg the acquire argument
     */
    private void doAcquireSharedInterruptibly(int arg)
        throws InterruptedException {
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return;
                    }
                }
                if (shouldParkAfterFailedAcquire(p, node) &&
                    parkAndCheckInterrupt())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    /**
     * Acquires in shared timed mode.
     *
     * @param arg the acquire argument
     * @param nanosTimeout max wait time
     * @return {@code true} if acquired
     */
    private boolean doAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (nanosTimeout <= 0L)
            return false;
        final long deadline = System.nanoTime() + nanosTimeout;
        final Node node = addWaiter(Node.SHARED);
        boolean failed = true;
        try {
            for (;;) {
                final Node p = node.predecessor();
                if (p == head) {
                    int r = tryAcquireShared(arg);
                    if (r >= 0) {
                        setHeadAndPropagate(node, r);
                        p.next = null; // help GC
                        failed = false;
                        return true;
                    }
                }
                nanosTimeout = deadline - System.nanoTime();
                if (nanosTimeout <= 0L)
                    return false;
                if (shouldParkAfterFailedAcquire(p, node) &&
                    nanosTimeout > spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if (Thread.interrupted())
                    throw new InterruptedException();
            }
        } finally {
            if (failed)
                cancelAcquire(node);
        }
    }

    // Main exported methods

    /**
     * 尝试以独占模式进行获取。
     *
     * 此方法应查询对象的状态是否允许以独占模式获取它，如果允许则获取它。
     * 此方法始终由执行获取的线程调用。
     * 如果此方法报告失败，则acquire方法可以将线程排队（如果尚未排队），
     * 直到其他某个线程释放该信号为止。
     *
     * 这可以用来实现方法Lock.tryLock() 。
     * 默认实现抛出UnsupportedOperationException 。
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return {@code true} if successful. Upon success, this object has
     *         been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryAcquire(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试设置状态以反映排他模式下的发布。
     * 始终由执行释放的线程调用此方法。
     * 默认实现抛出UnsupportedOperationException 。
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this object is now in a fully released
     *         state, so that any waiting threads may attempt to acquire;
     *         and {@code false} otherwise.
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if exclusive mode is not supported
     */
    protected boolean tryRelease(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试以共享模式进行获取。
     * 此方法应查询对象的状态是否允许以共享模式获取对象，如果允许则获取对象。
     * 此方法始终由执行获取的线程调用。
     * 如果此方法报告失败，则acquire方法可以将线程排队（如果尚未排队），直到其他某个线程释放该信号为止。
     * 默认实现抛出UnsupportedOperationException 。
     *
     * @param arg the acquire argument. This value is always the one
     *        passed to an acquire method, or is the value saved on entry
     *        to a condition wait.  The value is otherwise uninterpreted
     *        and can represent anything you like.
     * @return a negative value on failure; zero if acquisition in shared
     *         mode succeeded but no subsequent shared-mode acquire can
     *         succeed; and a positive value if acquisition in shared
     *         mode succeeded and subsequent shared-mode acquires might
     *         also succeed, in which case a subsequent waiting thread
     *         must check availability. (Support for three different
     *         return values enables this method to be used in contexts
     *         where acquires only sometimes act exclusively.)  Upon
     *         success, this object has been acquired.
     * @throws IllegalMonitorStateException if acquiring would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected int tryAcquireShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 尝试设置状态以反映共享模式下的发布。
     * 始终由执行释放的线程调用此方法。
     * 默认实现抛出UnsupportedOperationException 。
     *
     * @param arg the release argument. This value is always the one
     *        passed to a release method, or the current state value upon
     *        entry to a condition wait.  The value is otherwise
     *        uninterpreted and can represent anything you like.
     * @return {@code true} if this release of shared mode may permit a
     *         waiting acquire (shared or exclusive) to succeed; and
     *         {@code false} otherwise
     * @throws IllegalMonitorStateException if releasing would place this
     *         synchronizer in an illegal state. This exception must be
     *         thrown in a consistent fashion for synchronization to work
     *         correctly.
     * @throws UnsupportedOperationException if shared mode is not supported
     */
    protected boolean tryReleaseShared(int arg) {
        throw new UnsupportedOperationException();
    }

    /**
     * 如果仅相对于当前（调用）线程保持同步，则返回true 。
     * 每次调用非等待的AbstractQueuedSynchronizer.ConditionObject方法时，都会调用此方法。
     * （等待方法改为调用release 。）
     * 默认实现抛出UnsupportedOperationException 。
     * 此方法仅在AbstractQueuedSynchronizer.ConditionObject方法内部内部调用，
     * 因此如果不使用条件，则无需定义。
     *
     * @return {@code true} if synchronization is held exclusively;
     *         {@code false} otherwise
     * @throws UnsupportedOperationException if conditions are not supported
     */
    protected boolean isHeldExclusively() {
        throw new UnsupportedOperationException();
    }

    /**
     *
     * 入口，获取锁，成功则返回，否则进入等待。
     *
     * 在独占模式下获取，忽略中断。 通过至少调用一次tryAcquire ，并在成功后返回。
     * 否则，线程将排队，并可能反复阻塞和解除阻塞，
     * 并调用tryAcquire直到成功。 此方法可用于实现Lock.lock方法。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     */
    public final void acquire(int arg) {
        if (!tryAcquire(arg) &&
            acquireQueued(addWaiter(Node.EXCLUSIVE), arg))
            selfInterrupt();
    }

    /**
     * 以独占模式获取，如果中断则中止。
     * 通过首先检查中断状态，然后至少调用一次tryAcquire ，并成功返回。
     * 否则，线程将排队，并可能反复阻塞和解除阻塞，
     * 调用tryAcquire直到成功或线程被中断为止。
     * 此方法可用于实现Lock.lockInterruptibly方法。
     *
     * 普通方法在尝试获取锁的时候是不能响应中断的
     * 该方法将在收到中断信号后抛出异常。
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (!tryAcquire(arg))
            doAcquireInterruptibly(arg);
    }

    /**
     *
     * 在自旋获取锁的过程中，提供了超时与响应中断的处理。
     *
     * 尝试以互斥方式进行获取，如果被中断则中止，如果给定的超时时间过去，则失败。
     * 通过首先检查中断状态，然后至少调用一次tryAcquire ，并成功返回。
     * 否则，线程将排队，并可能反复阻塞和解除阻塞，调用tryAcquire直到成功或线程被中断或超时为止。
     * 此方法可用于实现方法Lock.tryLock(long, TimeUnit) 。
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquire} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquire(arg) ||
            doAcquireNanos(arg, nanosTimeout);
    }

    /**
     * 解锁，独占锁解锁
     * 以独占模式发布。
     * 调用子类实现的tryRelease，然后唤醒下一个等待的节点
     * 如果{@link #tryRelease}返回true，则通过解锁一个或多个线程来实现。
     * 此方法可用于实现方法{@link Lock#unlock}.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryRelease} but is otherwise uninterpreted and
     *        can represent anything you like.
     * @return the value returned from {@link #tryRelease}
     */
    public final boolean release(int arg) {
        if (tryRelease(arg)) {
            Node h = head;
            if (h != null && h.waitStatus != 0)
                unparkSuccessor(h);
            return true;
        }
        return false;
    }

    /**
     * Acquires in shared mode, ignoring interrupts.  Implemented by
     * first invoking at least once {@link #tryAcquireShared},
     * returning on success.  Otherwise the thread is queued, possibly
     * repeatedly blocking and unblocking, invoking {@link
     * #tryAcquireShared} until success.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     */
    public final void acquireShared(int arg) {
        if (tryAcquireShared(arg) < 0)
            doAcquireShared(arg);
    }

    /**
     * Acquires in shared mode, aborting if interrupted.  Implemented
     * by first checking interrupt status, then invoking at least once
     * {@link #tryAcquireShared}, returning on success.  Otherwise the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted.
     * @param arg the acquire argument.
     * This value is conveyed to {@link #tryAcquireShared} but is
     * otherwise uninterpreted and can represent anything
     * you like.
     * @throws InterruptedException if the current thread is interrupted
     */
    public final void acquireSharedInterruptibly(int arg)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        if (tryAcquireShared(arg) < 0)
            doAcquireSharedInterruptibly(arg);
    }

    /**
     * Attempts to acquire in shared mode, aborting if interrupted, and
     * failing if the given timeout elapses.  Implemented by first
     * checking interrupt status, then invoking at least once {@link
     * #tryAcquireShared}, returning on success.  Otherwise, the
     * thread is queued, possibly repeatedly blocking and unblocking,
     * invoking {@link #tryAcquireShared} until success or the thread
     * is interrupted or the timeout elapses.
     *
     * @param arg the acquire argument.  This value is conveyed to
     *        {@link #tryAcquireShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @param nanosTimeout the maximum number of nanoseconds to wait
     * @return {@code true} if acquired; {@code false} if timed out
     * @throws InterruptedException if the current thread is interrupted
     */
    public final boolean tryAcquireSharedNanos(int arg, long nanosTimeout)
            throws InterruptedException {
        if (Thread.interrupted())
            throw new InterruptedException();
        return tryAcquireShared(arg) >= 0 ||
            doAcquireSharedNanos(arg, nanosTimeout);
    }

    /**
     * Releases in shared mode.  Implemented by unblocking one or more
     * threads if {@link #tryReleaseShared} returns true.
     *
     * @param arg the release argument.  This value is conveyed to
     *        {@link #tryReleaseShared} but is otherwise uninterpreted
     *        and can represent anything you like.
     * @return the value returned from {@link #tryReleaseShared}
     */
    public final boolean releaseShared(int arg) {
        if (tryReleaseShared(arg)) {
            doReleaseShared();
            return true;
        }
        return false;
    }

    // Queue inspection methods

    /**
     * Queries whether any threads are waiting to acquire. Note that
     * because cancellations due to interrupts and timeouts may occur
     * at any time, a {@code true} return does not guarantee that any
     * other thread will ever acquire.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there may be other threads waiting to acquire
     */
    public final boolean hasQueuedThreads() {
        return head != tail;
    }

    /**
     * Queries whether any threads have ever contended to acquire this
     * synchronizer; that is if an acquire method has ever blocked.
     *
     * <p>In this implementation, this operation returns in
     * constant time.
     *
     * @return {@code true} if there has ever been contention
     */
    public final boolean hasContended() {
        return head != null;
    }

    /**
     * Returns the first (longest-waiting) thread in the queue, or
     * {@code null} if no threads are currently queued.
     *
     * <p>In this implementation, this operation normally returns in
     * constant time, but may iterate upon contention if other threads are
     * concurrently modifying the queue.
     *
     * @return the first (longest-waiting) thread in the queue, or
     *         {@code null} if no threads are currently queued
     */
    public final Thread getFirstQueuedThread() {
        // handle only fast path, else relay
        return (head == tail) ? null : fullGetFirstQueuedThread();
    }

    /**
     * Version of getFirstQueuedThread called when fastpath fails
     */
    private Thread fullGetFirstQueuedThread() {
        /*
         * The first node is normally head.next. Try to get its
         * thread field, ensuring consistent reads: If thread
         * field is nulled out or s.prev is no longer head, then
         * some other thread(s) concurrently performed setHead in
         * between some of our reads. We try this twice before
         * resorting to traversal.
         */
        Node h, s;
        Thread st;
        if (((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null) ||
            ((h = head) != null && (s = h.next) != null &&
             s.prev == head && (st = s.thread) != null))
            return st;

        /*
         * Head's next field might not have been set yet, or may have
         * been unset after setHead. So we must check to see if tail
         * is actually first node. If not, we continue on, safely
         * traversing from tail back to head to find first,
         * guaranteeing termination.
         */

        Node t = tail;
        Thread firstThread = null;
        while (t != null && t != head) {
            Thread tt = t.thread;
            if (tt != null)
                firstThread = tt;
            t = t.prev;
        }
        return firstThread;
    }

    /**
     * Returns true if the given thread is currently queued.
     *
     * <p>This implementation traverses the queue to determine
     * presence of the given thread.
     *
     * @param thread the thread
     * @return {@code true} if the given thread is on the queue
     * @throws NullPointerException if the thread is null
     */
    public final boolean isQueued(Thread thread) {
        if (thread == null)
            throw new NullPointerException();
        for (Node p = tail; p != null; p = p.prev)
            if (p.thread == thread)
                return true;
        return false;
    }

    /**
     * Returns {@code true} if the apparent first queued thread, if one
     * exists, is waiting in exclusive mode.  If this method returns
     * {@code true}, and the current thread is attempting to acquire in
     * shared mode (that is, this method is invoked from {@link
     * #tryAcquireShared}) then it is guaranteed that the current thread
     * is not the first queued thread.  Used only as a heuristic in
     * ReentrantReadWriteLock.
     */
    final boolean apparentlyFirstQueuedIsExclusive() {
        Node h, s;
        return (h = head) != null &&
            (s = h.next)  != null &&
            !s.isShared()         &&
            s.thread != null;
    }

    /**
     * 查询是否有任何线程在等待获取比当前线程更长的时间。
     * 调用此方法等效于（但可能比以下方法更有效）：
     *
     *  getFirstQueuedThread() != Thread.currentThread()
     *    && hasQueuedThreads()
     * 请注意，由于中断和超时引起的取消可能随时发生，因此返回true不能保证某些其他线程将在当前线程之前获取。
     * 同样，由于此队列为空，因此在此方法返回false之后，另一个线程也有可能赢得竞争。
     *
     * 此方法设计为由公平同步器使用以避免插拔。 如果此方法返回true ，
     * 则此类同步器的tryAcquire方法应返回false ，
     * 而其tryAcquireShared方法应返回负值（除非这是可重入获取）。
     * 例如，用于公平，可重入，排他模式同步器的tryAcquire方法可能如下所示：
     *
     *  protected boolean tryAcquire(int arg) {
     *    if (isHeldExclusively()) {
     *      // A reentrant acquire; increment hold count
     *      return true;
     *    } else if (hasQueuedPredecessors()) {
     *      return false;
     *    } else {
     *      // try to acquire normally
     *    }
     *  }
     *
     * @return {@code true} if there is a queued thread preceding the
     *         current thread, and {@code false} if the current thread
     *         is at the head of the queue or the queue is empty
     * @since 1.7
     */
    public final boolean hasQueuedPredecessors() {
        // 正确性取决于head是否在tail之前初始化，以及head.next是否正确（如果当前线程在队列中）。
        Node t = tail; // Read fields in reverse initialization order
        Node h = head;
        //h != t 表示当前有正在排队的
        Node s;
        return h != t &&
            ((s = h.next) == null || s.thread != Thread.currentThread());
    }


    // Instrumentation and monitoring methods

    /**
     * Returns an estimate of the number of threads waiting to
     * acquire.  The value is only an estimate because the number of
     * threads may change dynamically while this method traverses
     * internal data structures.  This method is designed for use in
     * monitoring system state, not for synchronization
     * control.
     *
     * @return the estimated number of threads waiting to acquire
     */
    public final int getQueueLength() {
        int n = 0;
        for (Node p = tail; p != null; p = p.prev) {
            if (p.thread != null)
                ++n;
        }
        return n;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate.  The elements of the
     * returned collection are in no particular order.  This method is
     * designed to facilitate construction of subclasses that provide
     * more extensive monitoring facilities.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            Thread t = p.thread;
            if (t != null)
                list.add(t);
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in exclusive mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to an exclusive acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getExclusiveQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (!p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a collection containing threads that may be waiting to
     * acquire in shared mode. This has the same properties
     * as {@link #getQueuedThreads} except that it only returns
     * those threads waiting due to a shared acquire.
     *
     * @return the collection of threads
     */
    public final Collection<Thread> getSharedQueuedThreads() {
        ArrayList<Thread> list = new ArrayList<Thread>();
        for (Node p = tail; p != null; p = p.prev) {
            if (p.isShared()) {
                Thread t = p.thread;
                if (t != null)
                    list.add(t);
            }
        }
        return list;
    }

    /**
     * Returns a string identifying this synchronizer, as well as its state.
     * The state, in brackets, includes the String {@code "State ="}
     * followed by the current value of {@link #getState}, and either
     * {@code "nonempty"} or {@code "empty"} depending on whether the
     * queue is empty.
     *
     * @return a string identifying this synchronizer, as well as its state
     */
    public String toString() {
        int s = getState();
        String q  = hasQueuedThreads() ? "non" : "";
        return super.toString() +
            "[State = " + s + ", " + q + "empty queue]";
    }


    // Internal support methods for Conditions

    /**
     * Returns true if a node, always one that was initially placed on
     * a condition queue, is now waiting to reacquire on sync queue.
     * @param node the node
     * @return true if is reacquiring
     */
    final boolean isOnSyncQueue(Node node) {
        if (node.waitStatus == Node.CONDITION || node.prev == null)
            return false;
        if (node.next != null) // If has successor, it must be on queue
            return true;
        /*
         * node.prev can be non-null, but not yet on queue because
         * the CAS to place it on queue can fail. So we have to
         * traverse from tail to make sure it actually made it.  It
         * will always be near the tail in calls to this method, and
         * unless the CAS failed (which is unlikely), it will be
         * there, so we hardly ever traverse much.
         */
        return findNodeFromTail(node);
    }

    /**
     * Returns true if node is on sync queue by searching backwards from tail.
     * Called only when needed by isOnSyncQueue.
     * @return true if present
     */
    private boolean findNodeFromTail(Node node) {
        Node t = tail;
        for (;;) {
            if (t == node)
                return true;
            if (t == null)
                return false;
            t = t.prev;
        }
    }

    /**
     * Transfers a node from a condition queue onto sync queue.
     * Returns true if successful.
     * @param node the node
     * @return true if successfully transferred (else the node was
     * cancelled before signal)
     */
    final boolean transferForSignal(Node node) {
        /*
         * If cannot change waitStatus, the node has been cancelled.
         */
        if (!compareAndSetWaitStatus(node, Node.CONDITION, 0))
            return false;

        /*
         * Splice onto queue and try to set waitStatus of predecessor to
         * indicate that thread is (probably) waiting. If cancelled or
         * attempt to set waitStatus fails, wake up to resync (in which
         * case the waitStatus can be transiently and harmlessly wrong).
         */
        Node p = enq(node);
        int ws = p.waitStatus;
        if (ws > 0 || !compareAndSetWaitStatus(p, ws, Node.SIGNAL))
            LockSupport.unpark(node.thread);
        return true;
    }

    /**
     * Transfers node, if necessary, to sync queue after a cancelled wait.
     * Returns true if thread was cancelled before being signalled.
     *
     * @param node the node
     * @return true if cancelled before the node was signalled
     */
    final boolean transferAfterCancelledWait(Node node) {
        if (compareAndSetWaitStatus(node, Node.CONDITION, 0)) {
            enq(node);
            return true;
        }
        /*
         * If we lost out to a signal(), then we can't proceed
         * until it finishes its enq().  Cancelling during an
         * incomplete transfer is both rare and transient, so just
         * spin.
         */
        while (!isOnSyncQueue(node))
            Thread.yield();
        return false;
    }

    /**
     * Invokes release with current state value; returns saved state.
     * Cancels node and throws exception on failure.
     * @param node the condition node for this wait
     * @return previous sync state
     */
    final int fullyRelease(Node node) {
        boolean failed = true;
        try {
            int savedState = getState();
            if (release(savedState)) {
                failed = false;
                return savedState;
            } else {
                throw new IllegalMonitorStateException();
            }
        } finally {
            if (failed)
                node.waitStatus = Node.CANCELLED;
        }
    }

    // Instrumentation methods for conditions

    /**
     * Queries whether the given ConditionObject
     * uses this synchronizer as its lock.
     *
     * @param condition the condition
     * @return {@code true} if owned
     * @throws NullPointerException if the condition is null
     */
    public final boolean owns(ConditionObject condition) {
        return condition.isOwnedBy(this);
    }

    /**
     * Queries whether any threads are waiting on the given condition
     * associated with this synchronizer. Note that because timeouts
     * and interrupts may occur at any time, a {@code true} return
     * does not guarantee that a future {@code signal} will awaken
     * any threads.  This method is designed primarily for use in
     * monitoring of the system state.
     *
     * @param condition the condition
     * @return {@code true} if there are any waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final boolean hasWaiters(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.hasWaiters();
    }

    /**
     * Returns an estimate of the number of threads waiting on the
     * given condition associated with this synchronizer. Note that
     * because timeouts and interrupts may occur at any time, the
     * estimate serves only as an upper bound on the actual number of
     * waiters.  This method is designed for use in monitoring of the
     * system state, not for synchronization control.
     *
     * @param condition the condition
     * @return the estimated number of waiting threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final int getWaitQueueLength(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitQueueLength();
    }

    /**
     * Returns a collection containing those threads that may be
     * waiting on the given condition associated with this
     * synchronizer.  Because the actual set of threads may change
     * dynamically while constructing this result, the returned
     * collection is only a best-effort estimate. The elements of the
     * returned collection are in no particular order.
     *
     * @param condition the condition
     * @return the collection of threads
     * @throws IllegalMonitorStateException if exclusive synchronization
     *         is not held
     * @throws IllegalArgumentException if the given condition is
     *         not associated with this synchronizer
     * @throws NullPointerException if the condition is null
     */
    public final Collection<Thread> getWaitingThreads(ConditionObject condition) {
        if (!owns(condition))
            throw new IllegalArgumentException("Not owner");
        return condition.getWaitingThreads();
    }

    /**
     * Condition implementation for a {@link
     * AbstractQueuedSynchronizer} serving as the basis of a {@link
     * Lock} implementation.
     *
     * <p>Method documentation for this class describes mechanics,
     * not behavioral specifications from the point of view of Lock
     * and Condition users. Exported versions of this class will in
     * general need to be accompanied by documentation describing
     * condition semantics that rely on those of the associated
     * {@code AbstractQueuedSynchronizer}.
     *
     * <p>This class is Serializable, but all fields are transient,
     * so deserialized conditions have no waiters.
     */
    public class ConditionObject implements Condition, java.io.Serializable {
        private static final long serialVersionUID = 1173984872572414699L;
        /** First node of condition queue. */
        private transient Node firstWaiter;
        /** Last node of condition queue. */
        private transient Node lastWaiter;

        /**
         * Creates a new {@code ConditionObject} instance.
         */
        public ConditionObject() { }

        // Internal methods

        /**
         * Adds a new waiter to wait queue.
         * @return its new wait node
         */
        private Node addConditionWaiter() {
            Node t = lastWaiter;
            // If lastWaiter is cancelled, clean out.
            if (t != null && t.waitStatus != Node.CONDITION) {
                unlinkCancelledWaiters();
                t = lastWaiter;
            }
            Node node = new Node(Thread.currentThread(), Node.CONDITION);
            if (t == null)
                firstWaiter = node;
            else
                t.nextWaiter = node;
            lastWaiter = node;
            return node;
        }

        /**
         * Removes and transfers nodes until hit non-cancelled one or
         * null. Split out from signal in part to encourage compilers
         * to inline the case of no waiters.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignal(Node first) {
            do {
                if ( (firstWaiter = first.nextWaiter) == null)
                    lastWaiter = null;
                first.nextWaiter = null;
            } while (!transferForSignal(first) &&
                     (first = firstWaiter) != null);
        }

        /**
         * Removes and transfers all nodes.
         * @param first (non-null) the first node on condition queue
         */
        private void doSignalAll(Node first) {
            lastWaiter = firstWaiter = null;
            do {
                Node next = first.nextWaiter;
                first.nextWaiter = null;
                transferForSignal(first);
                first = next;
            } while (first != null);
        }

        /**
         * Unlinks cancelled waiter nodes from condition queue.
         * Called only while holding lock. This is called when
         * cancellation occurred during condition wait, and upon
         * insertion of a new waiter when lastWaiter is seen to have
         * been cancelled. This method is needed to avoid garbage
         * retention in the absence of signals. So even though it may
         * require a full traversal, it comes into play only when
         * timeouts or cancellations occur in the absence of
         * signals. It traverses all nodes rather than stopping at a
         * particular target to unlink all pointers to garbage nodes
         * without requiring many re-traversals during cancellation
         * storms.
         */
        private void unlinkCancelledWaiters() {
            Node t = firstWaiter;
            Node trail = null;
            while (t != null) {
                Node next = t.nextWaiter;
                if (t.waitStatus != Node.CONDITION) {
                    t.nextWaiter = null;
                    if (trail == null)
                        firstWaiter = next;
                    else
                        trail.nextWaiter = next;
                    if (next == null)
                        lastWaiter = trail;
                }
                else
                    trail = t;
                t = next;
            }
        }

        // public methods

        /**
         * Moves the longest-waiting thread, if one exists, from the
         * wait queue for this condition to the wait queue for the
         * owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signal() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignal(first);
        }

        /**
         * Moves all threads from the wait queue for this condition to
         * the wait queue for the owning lock.
         *
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        public final void signalAll() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            Node first = firstWaiter;
            if (first != null)
                doSignalAll(first);
        }

        /**
         * Implements uninterruptible condition wait.
         * <ol>
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * </ol>
         */
        public final void awaitUninterruptibly() {
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean interrupted = false;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if (Thread.interrupted())
                    interrupted = true;
            }
            if (acquireQueued(node, savedState) || interrupted)
                selfInterrupt();
        }

        /*
         * For interruptible waits, we need to track whether to throw
         * InterruptedException, if interrupted while blocked on
         * condition, versus reinterrupt current thread, if
         * interrupted while blocked waiting to re-acquire.
         */

        /** Mode meaning to reinterrupt on exit from wait */
        private static final int REINTERRUPT =  1;
        /** Mode meaning to throw InterruptedException on exit from wait */
        private static final int THROW_IE    = -1;

        /**
         * Checks for interrupt, returning THROW_IE if interrupted
         * before signalled, REINTERRUPT if after signalled, or
         * 0 if not interrupted.
         */
        private int checkInterruptWhileWaiting(Node node) {
            return Thread.interrupted() ?
                (transferAfterCancelledWait(node) ? THROW_IE : REINTERRUPT) :
                0;
        }

        /**
         * Throws InterruptedException, reinterrupts current thread, or
         * does nothing, depending on mode.
         */
        private void reportInterruptAfterWait(int interruptMode)
            throws InterruptedException {
            if (interruptMode == THROW_IE)
                throw new InterruptedException();
            else if (interruptMode == REINTERRUPT)
                selfInterrupt();
        }

        /**
         * Implements interruptible condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled or interrupted.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final void await() throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                LockSupport.park(this);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null) // clean up if cancelled
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * </ol>
         */
        public final long awaitNanos(long nanosTimeout)
                throws InterruptedException {
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return deadline - System.nanoTime();
        }

        /**
         * Implements absolute timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean awaitUntil(Date deadline)
                throws InterruptedException {
            long abstime = deadline.getTime();
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (System.currentTimeMillis() > abstime) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                LockSupport.parkUntil(this, abstime);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        /**
         * Implements timed condition wait.
         * <ol>
         * <li> If current thread is interrupted, throw InterruptedException.
         * <li> Save lock state returned by {@link #getState}.
         * <li> Invoke {@link #release} with saved state as argument,
         *      throwing IllegalMonitorStateException if it fails.
         * <li> Block until signalled, interrupted, or timed out.
         * <li> Reacquire by invoking specialized version of
         *      {@link #acquire} with saved state as argument.
         * <li> If interrupted while blocked in step 4, throw InterruptedException.
         * <li> If timed out while blocked in step 4, return false, else true.
         * </ol>
         */
        public final boolean await(long time, TimeUnit unit)
                throws InterruptedException {
            long nanosTimeout = unit.toNanos(time);
            if (Thread.interrupted())
                throw new InterruptedException();
            Node node = addConditionWaiter();
            int savedState = fullyRelease(node);
            final long deadline = System.nanoTime() + nanosTimeout;
            boolean timedout = false;
            int interruptMode = 0;
            while (!isOnSyncQueue(node)) {
                if (nanosTimeout <= 0L) {
                    timedout = transferAfterCancelledWait(node);
                    break;
                }
                if (nanosTimeout >= spinForTimeoutThreshold)
                    LockSupport.parkNanos(this, nanosTimeout);
                if ((interruptMode = checkInterruptWhileWaiting(node)) != 0)
                    break;
                nanosTimeout = deadline - System.nanoTime();
            }
            if (acquireQueued(node, savedState) && interruptMode != THROW_IE)
                interruptMode = REINTERRUPT;
            if (node.nextWaiter != null)
                unlinkCancelledWaiters();
            if (interruptMode != 0)
                reportInterruptAfterWait(interruptMode);
            return !timedout;
        }

        //  support for instrumentation

        /**
         * Returns true if this condition was created by the given
         * synchronization object.
         *
         * @return {@code true} if owned
         */
        final boolean isOwnedBy(AbstractQueuedSynchronizer sync) {
            return sync == AbstractQueuedSynchronizer.this;
        }

        /**
         * Queries whether any threads are waiting on this condition.
         * Implements {@link AbstractQueuedSynchronizer#hasWaiters(ConditionObject)}.
         *
         * @return {@code true} if there are any waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final boolean hasWaiters() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    return true;
            }
            return false;
        }

        /**
         * Returns an estimate of the number of threads waiting on
         * this condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitQueueLength(ConditionObject)}.
         *
         * @return the estimated number of waiting threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final int getWaitQueueLength() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            int n = 0;
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION)
                    ++n;
            }
            return n;
        }

        /**
         * Returns a collection containing those threads that may be
         * waiting on this Condition.
         * Implements {@link AbstractQueuedSynchronizer#getWaitingThreads(ConditionObject)}.
         *
         * @return the collection of threads
         * @throws IllegalMonitorStateException if {@link #isHeldExclusively}
         *         returns {@code false}
         */
        protected final Collection<Thread> getWaitingThreads() {
            if (!isHeldExclusively())
                throw new IllegalMonitorStateException();
            ArrayList<Thread> list = new ArrayList<Thread>();
            for (Node w = firstWaiter; w != null; w = w.nextWaiter) {
                if (w.waitStatus == Node.CONDITION) {
                    Thread t = w.thread;
                    if (t != null)
                        list.add(t);
                }
            }
            return list;
        }
    }

    /**
     * Setup to support compareAndSet. We need to natively implement
     * this here: For the sake of permitting future enhancements, we
     * cannot explicitly subclass AtomicInteger, which would be
     * efficient and useful otherwise. So, as the lesser of evils, we
     * natively implement using hotspot intrinsics API. And while we
     * are at it, we do the same for other CASable fields (which could
     * otherwise be done with atomic field updaters).
     */
    private static final Unsafe unsafe = Unsafe.getUnsafe();
    private static final long stateOffset;
    private static final long headOffset;
    private static final long tailOffset;
    private static final long waitStatusOffset;
    private static final long nextOffset;

    static {
        try {
            stateOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("state"));
            headOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("head"));
            tailOffset = unsafe.objectFieldOffset
                (AbstractQueuedSynchronizer.class.getDeclaredField("tail"));
            waitStatusOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("waitStatus"));
            nextOffset = unsafe.objectFieldOffset
                (Node.class.getDeclaredField("next"));

        } catch (Exception ex) { throw new Error(ex); }
    }

    /**
     * CAS head field. Used only by enq.
     */
    private final boolean compareAndSetHead(Node update) {
        return unsafe.compareAndSwapObject(this, headOffset, null, update);
    }

    /**
     * CAS tail field. Used only by enq.
     */
    private final boolean compareAndSetTail(Node expect, Node update) {
        return unsafe.compareAndSwapObject(this, tailOffset, expect, update);
    }

    /**
     * CAS waitStatus field of a node.
     */
    private static final boolean compareAndSetWaitStatus(Node node,
                                                         int expect,
                                                         int update) {
        return unsafe.compareAndSwapInt(node, waitStatusOffset,
                                        expect, update);
    }

    /**
     * CAS next field of a node.
     */
    private static final boolean compareAndSetNext(Node node,
                                                   Node expect,
                                                   Node update) {
        return unsafe.compareAndSwapObject(node, nextOffset, expect, update);
    }
}
