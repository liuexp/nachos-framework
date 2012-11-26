package nachos.vm;

import nachos.machine.TranslationEntry;

public class VMPage {

	public VMPage(VMProcess p, TranslationEntry e){
		this.pid = p.pid;
		this.vpn = e.vpn;
	}
	
	public String toString(){
		return pid + "," + vpn;
	}
	
	public boolean equals(Object o){
		if(o == null)return false;
		return toString().equals(o.toString());
	}
	
	public int hashCode(){
		return toString().hashCode();
	}

	public int pid;
	public int vpn;

}
