import subprocess
import os
import glob

# Automatically find files to analyze
libs_moria = glob.glob('android-moria-firmware-extractor/libs/*')
libs_mithril = glob.glob('android-mithril-firmware-scanner/libs/*')
binaries = [
    'android-moria-firmware-extractor/fake_root/data/data/com.diamon.moria/files/usr/bin/moria',
    'android-mithril-firmware-scanner/fake_root/data/data/com.diamon.mithril/files/usr/bin/mithril'
]

files_to_analyze = binaries + libs_moria + libs_mithril

# Get set of all filenames to check "Presente en carpeta"
all_filenames = {os.path.basename(f) for f in files_to_analyze}

with open('REPORTE_ANALISIS_DEPENDENCIAS.md', 'w') as r:
    r.write('# Reporte de Dependencias\n\n')
    for f in sorted(files_to_analyze):
        filename = os.path.basename(f)
        # Skip directories if any
        if os.path.isdir(f): continue
        r.write(f'### {filename} ({f})\n| Dependencia | Clasificación | Presente en carpeta? |\n|---|---|---|\n')
        try:
            out = subprocess.check_output(['readelf', '-d', f], text=True)
            for line in out.splitlines():
                if '(NEEDED)' in line:
                    # Extract library name
                    if '[' in line and ']' in line:
                        d = line.split('[')[1].split(']')[0]
                        # Update classification to include common libraries
                        c = 'Sistema Android' if d in ['libc.so', 'libm.so', 'libdl.so', 'liblog.so', 'libz.so.1', 'libstdc++.so', 'libc++_shared.so'] else 'Externa'
                        present = "Sí" if d in all_filenames else "No"
                        r.write(f'| {d} | {c} | {present} |\n')
        except subprocess.CalledProcessError:
            r.write(f'| Error | No se pudo leer ELF | - |\n')
        r.write('\n')
