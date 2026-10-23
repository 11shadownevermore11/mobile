import sys, osmium
out, ins = sys.argv[1], sys.argv[2:]
mr = osmium.MergeInputReader()
for f in ins: mr.add_file(f)
w = osmium.SimpleWriter(out, overwrite=True)
mr.apply(w, simplify=True)
w.close()
