package nachos.vm;

import java.util.LinkedList;

import nachos.threads.Lock;
import nachos.machine.CoffSection;
import nachos.machine.Config;
import nachos.machine.Lib;
import nachos.machine.Machine;
import nachos.machine.Processor;
import nachos.machine.TranslationEntry;
import nachos.userprog.UserKernel;
import nachos.userprog.UserProcess;

/**
 * A <tt>UserProcess</tt> that supports demand-paging.
 */
public class VMProcess extends UserProcess {
	/**
	 * Allocate a new process.
	 */
	public VMProcess() {
		super();
	}

	/**
	 * Save the state of this process in preparation for a context switch.
	 * Called by <tt>UThread.saveState()</tt>.
	 */
	public void saveState() {
		Processor p = Machine.processor();
		for(int i=0;i<p.getTLBSize();i++){
			TranslationEntry e = p.readTLBEntry(i);
			if(e==null||!e.valid)continue;
			myTLB[i] = e.vpn;
			//p.writeTLBEntry(i, nullEntry);
		}
		
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Processor p = Machine.processor();
		Lib.debug(dbgProcess, "restoring states of "+pid);
		for(int i=0;i<p.getTLBSize();i++){
			TranslationEntry pageEntry = myTLB[i]!=null? getPage(this.pid, myTLB[i]) : null;
			if(pageEntry != null){	
				VMKernel.getKernel().putTLBEntry(i, pageEntry);
			}else VMKernel.getKernel().putTLBEntry(i, nullEntry);
			myTLB[i]=null;
			//if(numPages - stackPages - argPages <= myTLB[i].vpn)reqPage(new VMPage(this.pid, myTLB[i].vpn));
		}
		dumpTLB();
		dumpPageTable();
	}

	public static void dumpTLB() {
		Processor p = Machine.processor();
		Lib.debug(dbgVM, "=========TLB dump==========");
		for(int i=0;i<p.getTLBSize();i++){
			TranslationEntry e = p.readTLBEntry(i);
			if(!e.valid)continue;
			Lib.debug(dbgVM, e.vpn + ", ppn="+ e.ppn + ", dirty=" + e.dirty+ ", used=" + e.used+ ", readOnly=" + e.readOnly+ ", valid=" + e.valid);
		}
	}

	/**
	 * Initializes page tables for this process so that the executable can be
	 * demand-paged.
	 * 
	 * @return <tt>true</tt> if successful.
	 */
	protected boolean loadSections() {
		return true;
	}

	/**
	 * Release any resources allocated by <tt>loadSections()</tt>.
	 */
	protected void unloadSections() {
		tableLock.acquire();
		LinkedList<VMPage> toBeFreed = new LinkedList<VMPage>();
		for(VMPage k: VMKernel.getKernel().ipTable.keySet()){
			if(k.pid == this.pid){
				toBeFreed.add(k);
			}
		}
		VMKernel.getKernel().freeSwap(this);
		for(VMPage k: toBeFreed)
			freePage(k);
		tableLock.release();
		
		Processor p = Machine.processor();
		for(int i=0;i<p.getTLBSize();i++){
			VMKernel.getKernel().putTLBEntry(i, nullEntry);
		}
		coff.close();
	}

	/**
	 * Handle a user exception. Called by <tt>UserKernel.exceptionHandler()</tt>
	 * . The <i>cause</i> argument identifies which exception occurred; see the
	 * <tt>Processor.exceptionZZZ</tt> constants.
	 * 
	 * @param cause
	 *            the user exception that occurred.
	 */
	public void handleException(int cause) {
		Processor processor = Machine.processor();

		switch (cause) {
		case Processor.exceptionTLBMiss:
			int vaddr = processor.readRegister(Processor.regBadVAddr);
			int vpn = Processor.pageFromAddress(vaddr);
			int idx = Lib.random(processor.getTLBSize());
			
			Lib.debug(dbgVM, "TLB miss! "+vpn);
			for (int i=0;i<processor.getTLBSize();i++){
				if(!processor.readTLBEntry(i).valid){
					idx = i;
					break;
				}
			}
			tableLock.acquire();
			if(vpn<0)handleException(Processor.exceptionBusError);
			VMKernel.getKernel().putTLBEntry(idx, reqPage(pid, vpn));
			tableLock.release();
			Lib.debug(dbgVM, "TLB miss resolved! "+vpn);
			dumpPageTable();
			dumpTLB();
			break;
		default:
			super.handleException(cause);
			break;
		}
	}
	
	public static void dumpPageTable(){
		Lib.debug(dbgVM, "-----------page table dump---------");
		for(VMPage x : VMKernel.getKernel().ipTable.keySet()){
			TranslationEntry e = VMKernel.getKernel().ipTable.get(x);
			Lib.debug(dbgVM, x+":"+e.ppn+", readOnly="+e.readOnly);
		}
	}
	
	@Override
	public int copyVirtualMemory(int vaddr, byte[] data, int offset, int length, boolean read) {
		try{
			Lib.assertTrue(offset >= 0 && length >= 0
					&& offset + length <= data.length);
	
			Processor p = Machine.processor();
			byte[] memory = p.getMemory();
	
			if(vaddr < 0)handleException(Processor.exceptionAddressError);
			int curVpn = Processor.pageFromAddress(vaddr);
			int curOffset = offset;
			int tot = 0;
			int vOffset = Processor.offsetFromAddress(vaddr);
			while(length > 0){
				tableLock.acquire();
				TranslationEntry e = reqPage(this.pid,curVpn);
				int ppn = e.ppn;
				int paddr = Processor.makeAddress(ppn, vOffset); 
				int amount = Math.min(length, pageSize - vOffset);
				if (paddr < 0 || paddr >= memory.length || amount < 0)break;
	
				if(read){
					System.arraycopy(memory, paddr, data, curOffset, amount);
				}else{
					System.arraycopy(data, curOffset, memory, paddr, amount);
					e.dirty = true;
				}
				e.used = true;
	
				tableLock.release();
				curOffset += amount;
				
				tot += amount;
				curVpn++;
				length -= amount;
				vOffset = 0;
			}
	
			return tot;
		} catch(Exception e){
			e.printStackTrace();
			return 0;
		} finally{
			if(tableLock.isHeldByCurrentThread())
				tableLock.release();
		}
	}
	
	@Override
	protected int allocPage(int vpn) {
		Lib.assertTrue(tableLock.isHeldByCurrentThread());
		int ppn = -1;
		int pn = Machine.processor().getNumPhysPages();
		
		for(int i=0;i<pn;i++){
//			UserKernel.phyTableLock.acquire();
			if(UserKernel.phyTable[i] == null){
				UserKernel.phyTable[i]=new VMPage(this.pid, vpn);
				ppn=i;
//				UserKernel.phyTableLock.release();
				break;
			}
//			UserKernel.phyTableLock.release();
		}
		if(ppn<0){
			ppn = VMKernel.getKernel().swapOut();
//			UserKernel.phyTableLock.acquire();
			UserKernel.phyTable[ppn]=new VMPage(this.pid, vpn);
//			UserKernel.phyTableLock.release();	
		}
		VMKernel.getKernel().ipTable.put(UserKernel.phyTable[ppn], new TranslationEntry(vpn, ppn, true, false, false, false));
		return ppn;
	}
	
	public TranslationEntry getPage(int pid, int vpn){
		return getPage(new VMPage(pid,vpn));
	}

	public TranslationEntry getPage(VMPage page) {
		//Lib.assertTrue(tableLock.isHeldByCurrentThread());
		TranslationEntry ret =VMKernel.getKernel().ipTable.get(page);
		Lib.assertTrue(ret==null||ret.valid);
		return ret;
	}
	
	public TranslationEntry reqPage(VMPage page){
		Lib.assertTrue(tableLock.isHeldByCurrentThread());
		TranslationEntry ret = VMKernel.getKernel().ipTable.get(page);
		if(ret != null) return ret;
		int vpn = page.vpn;
		allocPage(vpn);
		if(VMKernel.getKernel().swapIn(page))return VMKernel.getKernel().ipTable.get(page);
		ret = VMKernel.getKernel().ipTable.get(page);
		
		if (numPages - stackPages - argPages > vpn){
			// load sections
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);

				Lib.debug(dbgVM, "\t(forced) initializing " + pid +":" + section.getName()
						+ " section (" + section.getLength() + " pages)");
				int firstVpn = section.getFirstVPN(); 
				if (firstVpn <= vpn && vpn < firstVpn + section.getLength()) {
					section.loadPage(vpn - firstVpn, ret.ppn);
					ret.readOnly = section.isReadOnly();
					ret.valid = true;
					break;
				}
			}
		}
		
		return ret;
	}

	public TranslationEntry reqPage(int pid, int vpn) {
		return reqPage(new VMPage(pid,vpn));
	}

	public void freePage(int pid, int vpn){
		freePage(new VMPage(pid,vpn));
	}

	public void freePage(VMPage p) {
		Lib.assertTrue(tableLock.isHeldByCurrentThread());
		TranslationEntry e = getPage(p);
		UserKernel.phyTableLock.acquire();
		UserKernel.phyTable[e.ppn]=null;
		UserKernel.phyTableLock.release();
		VMKernel.getKernel().ipTable.remove(p);
		e.ppn = -1;
		e.valid=false;
	}

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	public static Lock tableLock = new Lock();
	private Integer [] myTLB = new Integer[Machine.processor().getTLBSize()];
	protected final int argPages = Config.getInteger("Processor.numArgPages", 1);
	public static TranslationEntry nullEntry = new TranslationEntry(-1,-1,false,false,false,false);
}
