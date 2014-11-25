require 'rubygems'
require 'rubygems/dependency_installer'

class CljRbUtil
  class << self

    # Finds the first writeable entry in the gem path
    # If running jruby-complete, the first entry is inside the jar, so
    # is read-only
    def first_writeable_gem_path
      Gem.path.detect do |p|
        File.writable?(p)
      end
    end

    def add_gem_sources(sources, replace=false)
      if replace
        Gem.sources = sources
      else
        Gem.sources += sources
      end
    end

    # Creates a new Gem::DependencyInstaller
    def gem_installer(ignore_deps, force, install_dir)
      Gem::DependencyInstaller.new(:ignore_dependencies => ignore_deps,
                                   :force => force,
                                   :install_dir => install_dir)
    end

    def gem_installed?(name, version)
      Gem::Specification.any? { |g| g.name == name && g.version.to_s == version }
    end

    def assert(exp, actual)
      ret = exp == actual
      puts "FAIL: #{exp} != #{actual}" unless ret
      ret
    end

    def identity(x)
      x
    end

  end
end
