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
			myTLB[i] = p.readTLBEntry(i);
			p.writeTLBEntry(i, new TranslationEntry());
		}
	}

	/**
	 * Restore the state of this process after a context switch. Called by
	 * <tt>UThread.restoreState()</tt>.
	 */
	public void restoreState() {
		Processor p = Machine.processor();
		for(int i=0;i<p.getTLBSize();i++){
			if(myTLB[i]==null)continue;
			p.writeTLBEntry(i, myTLB[i]);
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
		for(VMPage k: toBeFreed)
			freePage(k);
		tableLock.release();
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
			for (int i=0;i<processor.getTLBSize();i++){
				if(!processor.readTLBEntry(i).valid){
					idx = i;
					break;
				}
			}
			TranslationEntry oldEntry = processor.readTLBEntry(idx);
			tableLock.acquire();
			if(oldEntry.valid){
				TranslationEntry page = getPage(this.pid, oldEntry.vpn);
				if(page != null){
					page.dirty |= oldEntry.dirty;
					page.used|= oldEntry.used;
				}
			}
			if(vpn<0)handleException(Processor.exceptionBusError);
			processor.writeTLBEntry(idx, reqPage(pid, vpn));
			tableLock.release();
			break;
		default:
			super.handleException(cause);
			break;
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
	
				if(read)
					System.arraycopy(memory, paddr, data, curOffset, amount);
				else
					System.arraycopy(data, curOffset, memory, paddr, amount);
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
		int ppn = -1;
		int pn = Machine.processor().getNumPhysPages();
		
		for(int i=0;i<pn;i++){
			UserKernel.phyTableLock.acquire();
			if(!UserKernel.phyTable[i]){
				UserKernel.phyTable[i]=true;
				ppn=i;
				UserKernel.phyTableLock.release();
				break;
			}
			UserKernel.phyTableLock.release();
		}
		//TODO:swap out
		if(ppn<0)
			handleException(Processor.exceptionBusError);
		VMKernel.getKernel().ipTable.put(new VMPage(this.pid, vpn), new TranslationEntry(vpn, ppn, true, false, false, false));
		return ppn;
	}
	
	public TranslationEntry getPage(int pid, int vpn){
		return getPage(new VMPage(pid,vpn));
	}

	public TranslationEntry getPage(VMPage page) {
		// TODO: load from swap
		return VMKernel.getKernel().ipTable.get(page);
	}
	
	public TranslationEntry reqPage(VMPage page){
		TranslationEntry ret = VMKernel.getKernel().ipTable.get(page);
		if(ret != null) return ret;
		int vpn = page.vpn;
		allocPage(vpn);
		ret = VMKernel.getKernel().ipTable.get(page);
		
		if (numPages - stackPages - argPages > vpn){
			// load sections
			for (int s = 0; s < coff.getNumSections(); s++) {
				CoffSection section = coff.getSection(s);

				Lib.debug(dbgProcess, "\t(forced) initializing " + section.getName()
						+ " section (" + section.getLength() + " pages)");
				int firstVpn = section.getFirstVPN(); 
				if (firstVpn <= vpn && vpn < firstVpn + section.getLength()) {
					section.loadPage(vpn - firstVpn, ret.ppn);
					ret.readOnly = section.isReadOnly();
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
		TranslationEntry e = VMKernel.getKernel().ipTable.get(p);
		Processor proc = Machine.processor();
		for(int i=0;i<proc.getTLBSize();i++){
			TranslationEntry oldEntry = proc.readTLBEntry(i);
			if(oldEntry.vpn == e.vpn){
				oldEntry.valid = false;
				proc.writeTLBEntry(i, oldEntry);
			}
		}
		UserKernel.phyTableLock.acquire();
		UserKernel.phyTable[e.ppn]=false;
		UserKernel.phyTableLock.release();
		VMKernel.getKernel().ipTable.remove(p);
		e.ppn = -1;
		e.valid=false;
	}

	private static final int pageSize = Processor.pageSize;
	private static final char dbgProcess = 'a';
	private static final char dbgVM = 'v';
	public static Lock tableLock = new Lock();
	private TranslationEntry [] myTLB = new TranslationEntry[Machine.processor().getTLBSize()];
	protected final int argPages = Config.getInteger("Processor.numArgPages", 1);

}
