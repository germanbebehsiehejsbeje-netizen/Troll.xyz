
import re

content = """
    // KillAura Settings
    private final EnumValue<AttackMode> mode = new EnumValue<>("Mode", "模式", AttackMode.v1_8);
    private final NumberValue<Double> aimRange = new NumberValue<>("Aim Range", "瞄准范围", 5.0, 1.0, 6.0, 0.1);
    private final NumberValue<Double> searchRange = new NumberValue<>("Search Range", "搜索范围", 10.0, 1.0, 20.0, 0.1);
    private final NumberValue<Double> minCps = new NumberValue<>("Min CPS", "最小攻击速度", 10.0, 1.0, 20.0, 1.0, () -> mode.is(AttackMode.v1_8));
    
    public enum AttackMode {
        v1_8, v1_9
    }
"""

number_pattern = re.compile(r'new\s+NumberValue(?:<[^>]+>)?\("([^"]+)",\s*"([^"]+)",\s*([0-9.]+),\s*([0-9.]+),\s*([0-9.]+),\s*([0-9.]+)')
enum_val_pattern = re.compile(r'new\s+EnumValue(?:<[^>]+>)?\("([^"]+)",\s*"([^"]+)",\s*(\w+)\.(\w+)')
enum_def_pattern = re.compile(r'enum\s+(\w+)\s*\{([^}]+)\}')
enum_def_pattern_relaxed = re.compile(r'(?:public|private|protected)?\s*enum\s+(\w+)\s*\{([^}]+)\}')

print("--- NumberValue Matches ---")
for match in number_pattern.finditer(content):
    print(match.groups())

print("\n--- EnumValue Matches ---")
for match in enum_val_pattern.finditer(content):
    print(match.groups())

print("\n--- Enum Def Matches ---")
for match in enum_def_pattern.finditer(content):
    print(f"Strict: {match.groups()}")

for match in enum_def_pattern_relaxed.finditer(content):
    print(f"Relaxed: {match.groups()}")
