import Modal from './Modal';
import { updateSettings, useSettings } from '../settings/settings';

// Sound and hints (P-41, P-42). Stored in this browser until account settings (V2a).
interface SettingsPanelProps {
  onClose: () => void;
}

export default function SettingsPanel({ onClose }: SettingsPanelProps) {
  const settings = useSettings();

  return (
    <Modal title="Settings" onClose={onClose}>
      <div className="settings-form">
        <Switch label="Mute all sound" on={settings.muted} onChange={(muted) => updateSettings({ muted })} />
        <Slider
          label="Music volume" value={settings.musicVolume} disabled={settings.muted}
          onChange={(musicVolume) => updateSettings({ musicVolume })}
        />
        <Slider
          label="Effects volume" value={settings.effectsVolume} disabled={settings.muted}
          onChange={(effectsVolume) => updateSettings({ effectsVolume })}
        />
        <Switch label="Show hints" on={settings.hints} onChange={(hints) => updateSettings({ hints })} />
      </div>
    </Modal>
  );
}

interface SwitchProps {
  label: string;
  on: boolean;
  onChange: (on: boolean) => void;
}

function Switch({ label, on, onChange }: SwitchProps) {
  return (
    <button type="button" className="toggle" role="switch" aria-checked={on} onClick={() => onChange(!on)}>
      <span className="toggle-track" aria-hidden="true">
        <span className="toggle-thumb" />
      </span>
      {label}
    </button>
  );
}

interface SliderProps {
  label: string;
  value: number;
  disabled: boolean;
  onChange: (value: number) => void;
}

function Slider({ label, value, disabled, onChange }: SliderProps) {
  return (
    <label className="settings-slider">
      <span>{label}</span>
      <input
        type="range" min={0} max={100} step={5} value={value} disabled={disabled}
        onChange={(e) => onChange(Number(e.target.value))}
      />
      <output>{value}</output>
    </label>
  );
}
