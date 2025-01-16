#!/bin/sh
#
# © 2023-2025 Nokia
# Licensed under the Apache License 2.0
# SPDX-License-Identifier: Apache-2.0
#

set -e -u

# Check cpufreq governor:
NEED_SET_GOV=""
for gov in $(cat /sys/devices/system/cpu/cpu*/cpufreq/scaling_governor 2> /dev/null)
do
  if [ "$gov" != "performance" ]; then
    NEED_SET_GOV="1"
  fi
done
if [ -z "$NEED_SET_GOV" ]; then
  echo "OK, cpufreq governor is 'performance'."
fi

# Check SMT/hyperthreading control:
_SMTCTRL=$(cat /sys/devices/system/cpu/smt/control)
if [ "$_SMTCTRL" = "off" ] || [ "$_SMTCTRL" = "forceoff" ] || [ "$_SMTCTRL" = "notsupported" ]; then
  NEED_SET_SMT=""
  echo "OK, SMT/hyperthreading is off."
else
  NEED_SET_SMT="1"
fi

# Check frequency boosting:
NEED_SET_IB=""
_intel_file="/sys/devices/system/cpu/intel_pstate/no_turbo"
NEED_SET_OB=""
_other_file="/sys/devices/system/cpu/cpufreq/boost"
if [ -e "$_intel_file" ]; then
  # Intel CPU
  if [ $(cat "$_intel_file") = "0" ]; then
    NEED_SET_IB="1"
  else
    echo "OK, intel freq boost is off"
  fi
else
  # Other CPU
  if [ $(cat "$_other_file") = "1" ]; then
    NEED_SET_OB="1"
  else
    echo "OK, freq boost is off"
  fi
fi


# Set cpufreq governor:
if [ -n "$NEED_SET_GOV" ]; then
  set -x
  sudo cpupower -c all frequency-set -g performance
  set +x
fi

# Set SMT/hyperthreading control:
if [ -n "$NEED_SET_SMT" ]; then
  set -x
  echo "off" | sudo tee /sys/devices/system/cpu/smt/control
  cat /sys/devices/system/cpu/smt/control
  set +x
fi

# Set frequency boosting:
if [ -n "$NEED_SET_IB" ]; then
  set -x
  echo "1" | sudo tee "$_intel_file"
  cat "$_intel_file"
  set +x
fi
if [ -n "$NEED_SET_OB" ]; then
  set -x
  echo "0" | sudo tee "$_other_file"
  cat "$_other_file"
  set +x
fi


# Print temperature:
echo "Temperature:"
sensors "*-isa-*" | grep -e "CPU:" -e "Core"
