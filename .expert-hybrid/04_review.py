from pathlib import Path
p=Path('app-phone/src/main/java/com/code2hack/eyebrowse/phone/HostingController.kt')
s=p.read_text()
a=s.index('    /**\n     * Records one layout of the CURRENT STARTED Phone UI.')
b=s.index('    @Synchronized\n    fun onPhoneViewportChanged',a)
s=s[:a]+'''    /**
     * Records current Phone content bounds only. Owner identity rejects predecessor callbacks.
     * Private geometry always comes from the immutable generation profile, never these metrics.
     */
'''+s[b:]
a=s.index('    /**\n     * Moves the live WebView from the Phone UI into the private presentation, reconciling the')
b=s.index('    private fun hostOffscreenWithReconciledGeometry',a)
s=s[:a]+'''    /** Moves the live view onto the generation's immutable private profile, without navigation. */
'''+s[b:]
s=s.replace('private geometry to the last measured Phone content viewport','private geometry to its immutable presentation profile')
p.write_text(s)
print('HYBRID_PATCH_READY')
